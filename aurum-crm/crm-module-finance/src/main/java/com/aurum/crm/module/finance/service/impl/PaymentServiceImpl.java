package com.aurum.crm.module.finance.service.impl;

import cn.hutool.core.util.IdUtil;
import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.common.excel.ExcelKit;
import com.aurum.crm.common.excel.SpreadsheetXml;
import com.aurum.crm.module.crm.mapper.SysUserMiniMapper;
import com.aurum.crm.module.crm.model.StarBizType;
import com.aurum.crm.module.crm.model.SysUserMiniDO;
import com.aurum.crm.module.crm.service.StarService;
import com.aurum.crm.module.finance.mapper.CrmContractMiniMapper;
import com.aurum.crm.module.finance.mapper.CrmCustomerMiniMapper;
import com.aurum.crm.module.finance.mapper.PaymentMapper;
import com.aurum.crm.module.finance.model.CrmContractMiniDO;
import com.aurum.crm.module.finance.model.CrmCustomerMiniDO;
import com.aurum.crm.module.finance.model.FinanceDictLabels;
import com.aurum.crm.module.finance.model.PaymentDO;
import com.aurum.crm.module.finance.model.convert.PaymentConvert;
import com.aurum.crm.module.finance.model.dto.PaymentAuditReqVO;
import com.aurum.crm.module.finance.model.dto.PaymentPageReqVO;
import com.aurum.crm.module.finance.model.dto.PaymentSaveReqVO;
import com.aurum.crm.module.finance.model.vo.PaymentExportRowVO;
import com.aurum.crm.module.finance.model.vo.PaymentRespVO;
import com.aurum.crm.module.finance.service.FinanceNumberService;
import com.aurum.crm.module.finance.service.PaymentService;
import com.aurum.crm.module.system.service.OssStorageService;
import com.aurum.crm.mybatis.core.PageParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PaymentServiceImpl implements PaymentService {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String XML = "application/vnd.ms-excel";

    private static final String SCOPE_ALL = "all";
    private static final String SCOPE_MINE = "mine";
    private static final String SCOPE_SUBORDINATE = "subordinate";
    private static final String SCOPE_STARRED = "starred";
    /** 下属遍历的最大深度，防止 superior_id 成环时死循环 */
    private static final int MAX_SUBORDINATE_DEPTH = 20;

    /** 审核状态：待提审（{@code fin_payment.audit_status}）—— 新建后的初始状态，可自由编辑 */
    private static final int AUDIT_PENDING_SUBMIT = 0;
    /** 审核状态：审批中，只有这个状态可以审 */
    private static final int AUDIT_IN_PROGRESS = 1;
    /** 审核状态：已通过 */
    private static final int AUDIT_PASSED = 2;
    /** 审核状态：已驳回 */
    private static final int AUDIT_REJECTED = 3;
    /** 单据状态：待确认（{@code fin_payment.status}） */
    private static final int STATUS_PENDING = 0;
    /** 单据状态：已确认 —— 只有它算「钱已到账」 */
    private static final int STATUS_CONFIRMED = 1;

    @Resource private PaymentMapper paymentMapper;
    @Resource private PaymentConvert paymentConvert;
    @Resource private OssStorageService ossStorageService;
    @Resource private CrmContractMiniMapper crmContractMiniMapper;
    @Resource private CrmCustomerMiniMapper crmCustomerMiniMapper;
    @Resource private FinanceNumberService numberService;
    @Resource private StarService starService;
    @Resource private SysUserMiniMapper sysUserMiniMapper;

    @Override
    public PageResult<PaymentRespVO> pageQuery(PaymentPageReqVO req) {
        LambdaQueryWrapper<PaymentDO> wrapper = wrapper(req);
        // 胶囊口径（scope）在数据权限之内再收窄；audit-list 前端不传 scope，故天然走 all。
        if (!applyScope(wrapper, req.getScope())) {
            return emptyResult(req);
        }
        Page<PaymentDO> page = paymentMapper.selectPage(req,
                wrapper.orderByDesc(PaymentDO::getCreateTime).orderByDesc(PaymentDO::getPaymentId));
        List<PaymentDO> records = page.getRecords();
        // 批量补值：合同 / 客户 / 各合同已回款汇总，避免逐行查库（N+1）。
        // 这些补值查询都绕过数据权限，见各 mini mapper 的 javadoc。
        List<Long> contractIds = records.stream().map(PaymentDO::getContractId)
                .filter(Objects::nonNull).distinct().toList();
        List<Long> customerIds = records.stream().map(PaymentDO::getCustomerId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, CrmContractMiniDO> contracts = contractIds.isEmpty() ? Map.of()
                : crmContractMiniMapper.selectListNoPerm(contractIds).stream()
                        .collect(Collectors.toMap(CrmContractMiniDO::getContractId, c -> c, (a, b) -> a));
        Map<Long, String> customerNames = customerIds.isEmpty() ? Map.of()
                : crmCustomerMiniMapper.selectListNoPerm(customerIds).stream()
                        .collect(Collectors.toMap(CrmCustomerMiniDO::getCustomerId, CrmCustomerMiniDO::getCustomerName, (a, b) -> a));
        Map<Long, BigDecimal> received = new HashMap<>();
        for (Long contractId : contractIds) {
            // 列表的「未回款金额」用到账口径：只算已通过 + 已确认的钱
            received.put(contractId, paymentMapper.sumReceivedByContract(contractId));
        }
        // 批量查关注态，避免逐行查库；空页时 starredBizIds 返回空集
        List<Long> paymentIds = records.stream().map(PaymentDO::getPaymentId)
                .filter(Objects::nonNull).toList();
        Set<Long> starredIds = starService.starredBizIds(StarBizType.PAYMENT, paymentIds);
        List<PaymentRespVO> list = records.stream().map(p -> {
            PaymentRespVO vo = toVO(p, contracts, customerNames, received);
            vo.setStarred(starredIds.contains(p.getPaymentId()));
            return vo;
        }).toList();
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public PageResult<PaymentRespVO> auditPage(PaymentPageReqVO req) {
        req.setAuditStatus(AUDIT_IN_PROGRESS);
        // 审核待办页是「待我审的单子」，不是用户自选口径的列表：忽略 scope，
        // 免得前端哪天顺手带上 capsule 参数，把整个待办列表筛空 —— 财务会以为
        // 没有待办，而那些单子其实正等着被审。与 InvoiceServiceImpl#auditPage 同一处理。
        req.setScope(null);
        return pageQuery(req);
    }

    @Override
    public PaymentRespVO get(Long id) {
        PaymentRespVO vo = toVO(required(id));
        vo.setStarred(starService.isStarred(StarBizType.PAYMENT, id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PaymentSaveReqVO req) {
        CrmContractMiniDO contract = validateTotal(req.getContractId(), req.getAmount(), null);
        LoginUser user = LoginUserHolder.getRequired();
        PaymentDO payment = new PaymentDO();
        payment.setPaymentId(IdUtil.getSnowflakeNextId());
        Map<String, Object> numberContext = new HashMap<>();
        numberContext.put("customerId", contract.getCustomerId());
        if (user.getDeptId() != null) numberContext.put("deptId", user.getDeptId());
        payment.setPaymentNo(numberService.nextPaymentNumber(numberContext));
        fill(payment, req, contract);
        payment.setOwnerUserId(user.getUserId());
        payment.setDeptId(user.getDeptId());
        payment.setAuditStatus(AUDIT_PENDING_SUBMIT);
        payment.setStatus(STATUS_PENDING);
        paymentMapper.insert(payment);
        return payment.getPaymentId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PaymentSaveReqVO req) {
        editable(required(id));
        CrmContractMiniDO contract = validateTotal(req.getContractId(), req.getAmount(), id);
        PaymentDO update = new PaymentDO();
        update.setPaymentId(id);
        fill(update, req, contract);
        paymentMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        editable(required(id));
        paymentMapper.deleteById(id);
    }

    /**
     * 提交财务审核。
     *
     * <p><b>为什么要在这里重新校验额度</b>：提交是从「不占用」变成「占用」的转换点，
     * 而占用口径（{@link PaymentMapper#sumCommittedByContract}）把「已驳回」视为不占用
     * ——否则被驳回的单据会把合同额度永久锁死。由此产生一条必须堵住的路径：
     * <pre>
     *   合同 10 万：P1 登记 10 万 → 提交 → 财务驳回（占用归 0）
     *              P2 登记 10 万 → 提交（此时占用 10 万，校验通过）
     *              P1 重新提交 ← 若不校验，两笔合计 20 万都能进审批、都能被通过
     * </pre>
     * 让 create / update / submit 三个入口共用同一道 {@link #validateTotal} 校验。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        PaymentDO payment = required(id);
        editable(payment);
        // 排除自身：本单此刻的金额正在被校验，不该算进「他人已占用」里重复累加
        validateTotal(payment.getContractId(), payment.getAmount(), id);
        // 判断下推到 WHERE（同 audit）：editable() 只是读检查，与并发的
        // 提交 / 删除 / 审核交错时没有裁决力，两人的结论会互相覆盖。
        // 这两个状态值是 audit() 条件更新的前置状态，必须同名同值。
        int claimed = paymentMapper.update(new PaymentDO(), Wrappers.<PaymentDO>lambdaUpdate()
                .eq(PaymentDO::getPaymentId, id)
                .in(PaymentDO::getAuditStatus, AUDIT_PENDING_SUBMIT, AUDIT_REJECTED)
                .eq(PaymentDO::getStatus, STATUS_PENDING)
                .set(PaymentDO::getAuditStatus, AUDIT_IN_PROGRESS)
                .set(PaymentDO::getStatus, STATUS_PENDING));
        if (claimed == 0) {
            throw new ServiceException(ErrorCode.RESOURCE_ALREADY_TAKEN, "该回款已被处理，请刷新后重试");
        }
    }

    /**
     * 财务审核回款。
     *
     * <p>通过 → {@code audit_status = 2 已通过}、{@code status = 1 已确认}；
     * 驳回 → {@code audit_status = 3 已驳回}、{@code status} 保持 {@code 0 待确认}。
     * 只有「审批中」的单子可以审，其余一律拒绝。
     *
     * <p><b>为什么用条件更新而不是「先读再写」</b>：从 {@code required(id)} 读到状态
     * 到 {@code updateById} 落库之间存在窗口，两个人同时审同一笔（一个通过、一个驳回）
     * 会双双通过状态检查，最终结果由谁后写决定 —— 审批结论可能被静默覆盖。
     * 这里把状态判断下推到 {@code WHERE audit_status = 1}，受影响行数为 0
     * 就说明单据已被别人处理，与 {@code ContractServiceImpl#audit} 里
     * {@code claimed == 0} 是同一手法。
     *
     * <p>下面那次前置读取<b>只用来选提示语，不参与并发判定</b>：它区分得出
     * 「压根没提交过」（0 待提审 → 该提示「不在审核中」）和
     * 「提交过但刚被别人审掉了」（竞态 → 该提示「已被处理」），
     * 而这两句话对用户的意义完全不同。真正的裁决权仍在条件更新 ——
     * 读到的状态可能在读到之后、更新之前就被改掉，所以不能拿它做判断依据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, PaymentAuditReqVO req) {
        // 先按数据权限确认单据存在且可见：查不到直接 3001，不泄露「存在但看不到」
        PaymentDO payment = required(id);
        if (!Integer.valueOf(AUDIT_IN_PROGRESS).equals(payment.getAuditStatus())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "该回款当前不在审核中");
        }
        boolean passed = Integer.valueOf(1).equals(req.getResult());
        if (!passed && !StringUtils.hasText(req.getComment())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "驳回时必须填写审核意见");
        }
        // ⚠️ 第一个参数必须传实体（哪怕是个空壳），<b>不能传 null</b>：
        //    本次审核是唯一记录「谁审的、什么时候审的」的地方（审核意见 comment
        //    并不落库），而 MyBatis-Plus 的 updateFill 只在入参是实体时触发 ——
        //    传 null 会让 update_by / update_time 静默保持原值，审批痕迹直接丢失。
        int claimed = paymentMapper.update(new PaymentDO(), Wrappers.<PaymentDO>lambdaUpdate()
                .eq(PaymentDO::getPaymentId, id)
                .eq(PaymentDO::getAuditStatus, AUDIT_IN_PROGRESS)
                .set(PaymentDO::getAuditStatus, passed ? AUDIT_PASSED : AUDIT_REJECTED)
                .set(PaymentDO::getStatus, passed ? STATUS_CONFIRMED : STATUS_PENDING));
        if (claimed == 0) {
            throw new ServiceException(ErrorCode.RESOURCE_ALREADY_TAKEN, "该回款已被处理，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void star(Long id) {
        required(id);
        starService.star(StarBizType.PAYMENT, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unstar(Long id) {
        required(id);
        starService.unstar(StarBizType.PAYMENT, id);
    }

    @Override
    public String export(PaymentPageReqVO req) {
        byte[] bytes = ExcelKit.write(exportRows(req), PaymentExportRowVO.class, "回款");
        return ossStorageService.buildUrl(ossStorageService.upload(bytes, "export", XLSX, "xlsx"));
    }

    @Override
    public String exportXml(PaymentPageReqVO req) {
        byte[] bytes = SpreadsheetXml.write(exportRows(req), PaymentExportRowVO.class, "回款");
        return ossStorageService.buildUrl(ossStorageService.upload(bytes, "export", XML, "xml"));
    }

    private List<PaymentExportRowVO> exportRows(PaymentPageReqVO req) {
        LambdaQueryWrapper<PaymentDO> w = wrapper(req);
        // 导出复用列表的胶囊口径（scope）；数据权限仍由拦截器兜底
        if (!applyScope(w, req.getScope())) {
            return List.of();
        }
        return paymentMapper.selectList(w.orderByDesc(PaymentDO::getCreateTime))
                .stream().map(this::toExportRow).toList();
    }

    private LambdaQueryWrapper<PaymentDO> wrapper(PaymentPageReqVO req) {
        LambdaQueryWrapper<PaymentDO> w = Wrappers.lambdaQuery();
        if (StringUtils.hasText(req.getKeyword())) w.like(PaymentDO::getPaymentNo, req.getKeyword().trim());
        if (StringUtils.hasText(req.getPaymentNo())) w.like(PaymentDO::getPaymentNo, req.getPaymentNo());
        return w.eq(req.getCustomerId() != null, PaymentDO::getCustomerId, req.getCustomerId())
                .eq(req.getContractId() != null, PaymentDO::getContractId, req.getContractId())
                .eq(req.getOwnerUserId() != null, PaymentDO::getOwnerUserId, req.getOwnerUserId())
                .eq(req.getAuditStatus() != null, PaymentDO::getAuditStatus, req.getAuditStatus())
                .eq(req.getStatus() != null, PaymentDO::getStatus, req.getStatus());
    }

    /**
     * 胶囊口径过滤（{@code scope}），与数据权限拦截器是<b>交集</b>关系 ——
     * 用户主动选的取数口径只能在数据权限允许的范围内继续收窄。
     *
     * <p>只追加裸的 {@code eq} / {@code in} 连接词，绝不追加裸 {@code or}。
     *
     * @return {@code false} 表示结果必然为空，调用方应直接返回空页。
     */
    private boolean applyScope(LambdaQueryWrapper<PaymentDO> wrapper, String scope) {
        if (!StringUtils.hasText(scope) || SCOPE_ALL.equals(scope)) {
            return true;
        }
        LoginUser me = LoginUserHolder.getRequired();
        switch (scope) {
            case SCOPE_MINE -> {
                if (me.getUserId() == null) {
                    return false;
                }
                wrapper.eq(PaymentDO::getOwnerUserId, me.getUserId());
            }
            case SCOPE_SUBORDINATE -> {
                List<Long> subordinateIds = listSubordinateUserIds(me.getUserId());
                if (subordinateIds.isEmpty()) {
                    return false;
                }
                wrapper.in(PaymentDO::getOwnerUserId, subordinateIds);
            }
            case SCOPE_STARRED -> {
                Set<Long> starredIds = starService.starredBizIds(StarBizType.PAYMENT);
                if (starredIds.isEmpty()) {
                    return false;
                }
                wrapper.in(PaymentDO::getPaymentId, starredIds);
            }
            // 未登记的取值不静默放行，直接 400 更容易发现前端漏配
            default -> throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "不支持的 scope 取值：" + scope);
        }
        return true;
    }

    /**
     * 沿 {@code sys_user.superior_id} 自顶向下逐层收集「我」的全部下属（多层，<b>不含自己</b>）。
     * 与 {@code ContractServiceImpl#listSubordinateUserIds} 同构。
     */
    private List<Long> listSubordinateUserIds(Long rootUserId) {
        if (rootUserId == null) {
            return List.of();
        }
        Set<Long> visited = new HashSet<>();
        visited.add(rootUserId);
        List<Long> result = new ArrayList<>();
        List<Long> frontier = List.of(rootUserId);
        for (int depth = 0; depth < MAX_SUBORDINATE_DEPTH && !frontier.isEmpty(); depth++) {
            List<SysUserMiniDO> children =
                    sysUserMiniMapper.selectList(SysUserMiniDO::getSuperiorId, frontier);
            List<Long> next = new ArrayList<>();
            for (SysUserMiniDO child : children) {
                if (child.getUserId() != null && visited.add(child.getUserId())) {
                    result.add(child.getUserId());
                    next.add(child.getUserId());
                }
            }
            frontier = next;
        }
        return result;
    }

    /** 分页空结果：筛选条件失效不该中断查询 */
    private <T> PageResult<T> emptyResult(PageParam reqVO) {
        Page<?> page = reqVO.toPage();
        return PageResult.empty((int) page.getCurrent(), (int) page.getSize());
    }

    /** 单条补值（详情），绕过数据权限读关联合同 / 客户 */
    private PaymentRespVO toVO(PaymentDO payment) {
        PaymentRespVO vo = paymentConvert.toRespVO(payment);
        CrmContractMiniDO contract = crmContractMiniMapper.selectByIdNoPerm(payment.getContractId());
        if (contract != null) {
            vo.setContractName(contract.getContractName());
            // 详情与列表同一口径（到账口径），否则同一笔单子两处显示不一致
            BigDecimal received = paymentMapper.sumReceivedByContract(contract.getContractId());
            vo.setUnpaidAmount(contract.getAmount().subtract(received).max(BigDecimal.ZERO));
        }
        CrmCustomerMiniDO customer = crmCustomerMiniMapper.selectByIdNoPerm(payment.getCustomerId());
        if (customer != null) vo.setCustomerName(customer.getCustomerName());
        return vo;
    }

    /** 批量补值（列表），合同 / 客户 / 已回款汇总都已预先查好 */
    private PaymentRespVO toVO(PaymentDO payment, Map<Long, CrmContractMiniDO> contracts,
                               Map<Long, String> customerNames, Map<Long, BigDecimal> received) {
        PaymentRespVO vo = paymentConvert.toRespVO(payment);
        CrmContractMiniDO contract = contracts.get(payment.getContractId());
        if (contract != null) {
            vo.setContractName(contract.getContractName());
            vo.setUnpaidAmount(contract.getAmount()
                    .subtract(received.getOrDefault(contract.getContractId(), BigDecimal.ZERO))
                    .max(BigDecimal.ZERO));
        }
        vo.setCustomerName(customerNames.get(payment.getCustomerId()));
        return vo;
    }

    private CrmContractMiniDO validateTotal(Long contractId, BigDecimal amount, Long excludeId) {
        CrmContractMiniDO contract = crmContractMiniMapper.selectByIdForUpdate(contractId);
        if (contract == null) throw new ServiceException(ErrorCode.CONTRACT_NOT_EXISTS);
        // 校验用占用口径：审核中的单据也占额度（防重复登记），但被驳回的要释放
        BigDecimal registered = paymentMapper.sumCommittedByContract(contractId, excludeId);
        if (registered.add(amount).compareTo(contract.getAmount()) > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "累计回款不能超过合同金额，当前还可登记 " + contract.getAmount().subtract(registered) + " 元");
        }
        return contract;
    }

    private void fill(PaymentDO payment, PaymentSaveReqVO req, CrmContractMiniDO contract) {
        payment.setContractId(contract.getContractId());
        payment.setCustomerId(contract.getCustomerId());
        payment.setAmount(req.getAmount());
        payment.setPaymentDate(req.getPaymentDate());
        payment.setPaymentMethod(req.getPaymentMethod());
        payment.setAccountName(req.getAccountName());
        payment.setAccountNo(req.getAccountNo());
        payment.setSerialNo(req.getSerialNo());
        payment.setRemark(req.getRemark());
    }

    private PaymentDO required(Long id) {
        PaymentDO payment = paymentMapper.selectById(id);
        if (payment == null) throw new ServiceException(ErrorCode.PAYMENT_NOT_EXISTS);
        return payment;
    }

    private void editable(PaymentDO payment) {
        boolean draftOrRejected = Integer.valueOf(AUDIT_PENDING_SUBMIT).equals(payment.getAuditStatus())
                || Integer.valueOf(AUDIT_REJECTED).equals(payment.getAuditStatus());
        if (!draftOrRejected || !Integer.valueOf(STATUS_PENDING).equals(payment.getStatus())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "审核中或已确认的回款不允许编辑或删除");
        }
    }

    private PaymentExportRowVO toExportRow(PaymentDO p) {
        PaymentExportRowVO row = new PaymentExportRowVO();
        row.setPaymentNo(p.getPaymentNo());
        row.setContractId(String.valueOf(p.getContractId()));
        row.setCustomerId(String.valueOf(p.getCustomerId()));
        row.setAmount(p.getAmount());
        row.setPaymentMethod(FinanceDictLabels.paymentMethod(p.getPaymentMethod()));
        row.setPaymentDate(p.getPaymentDate());
        row.setAccountName(p.getAccountName());
        row.setAuditStatus(FinanceDictLabels.auditStatus(p.getAuditStatus()));
        row.setStatus(FinanceDictLabels.paymentStatus(p.getStatus()));
        row.setOwnerUserId(String.valueOf(p.getOwnerUserId()));
        row.setCreateTime(p.getCreateTime());
        return row;
    }
}
