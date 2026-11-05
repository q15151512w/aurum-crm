package com.aurum.crm.module.finance.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
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
import com.aurum.crm.module.crm.support.SubordinateUserIds;
import com.aurum.crm.module.finance.mapper.CrmContractMiniMapper;
import com.aurum.crm.module.finance.mapper.CrmCustomerMiniMapper;
import com.aurum.crm.module.finance.mapper.CustomerInvoiceInfoMiniMapper;
import com.aurum.crm.module.finance.mapper.InvoiceMapper;
import com.aurum.crm.module.finance.mapper.InvoicePaymentMapper;
import com.aurum.crm.module.finance.mapper.PaymentMapper;
import com.aurum.crm.module.finance.model.CrmContractMiniDO;
import com.aurum.crm.module.finance.model.CrmCustomerInvoiceInfoMiniDO;
import com.aurum.crm.module.finance.model.CrmCustomerMiniDO;
import com.aurum.crm.module.finance.model.FinanceDictLabels;
import com.aurum.crm.module.finance.model.InvoiceDO;
import com.aurum.crm.module.finance.model.InvoicePaymentDO;
import com.aurum.crm.module.finance.model.PaymentDO;
import com.aurum.crm.module.finance.model.convert.InvoiceConvert;
import com.aurum.crm.module.finance.model.dto.InvoiceIssueReqVO;
import com.aurum.crm.module.finance.model.dto.InvoicePageReqVO;
import com.aurum.crm.module.finance.model.dto.InvoicePaymentReqVO;
import com.aurum.crm.module.finance.model.dto.InvoiceRejectReqVO;
import com.aurum.crm.module.finance.model.dto.InvoiceSaveReqVO;
import com.aurum.crm.module.finance.model.vo.CustomerInvoiceInfoVO;
import com.aurum.crm.module.finance.model.vo.InvoiceCustomerFormRespVO;
import com.aurum.crm.module.finance.model.vo.InvoiceExportRowVO;
import com.aurum.crm.module.finance.model.vo.InvoicePaymentRespVO;
import com.aurum.crm.module.finance.model.vo.InvoiceRespVO;
import com.aurum.crm.module.finance.model.vo.InvoiceablePaymentVO;
import com.aurum.crm.module.finance.model.vo.PaymentAllocatedVO;
import com.aurum.crm.module.finance.service.FinanceNumberService;
import com.aurum.crm.module.finance.service.InvoiceService;
import com.aurum.crm.module.system.dal.dataobject.AttachmentDO;
import com.aurum.crm.mybatis.core.PageParam;
import com.aurum.crm.module.system.dal.mysql.AttachmentMapper;
import com.aurum.crm.module.system.service.OssStorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 发票 Service 实现。
 *
 * <p>数据模型是原型所示的「<b>一票多回款</b>」：一张发票挂多条回款明细，
 * 每行有自己的「本次开票金额」。{@code fin_invoice_payment} 是唯一事实来源，
 * {@code fin_invoice.payment_id} 降级为「主回款」（取首条明细）。
 *
 * <p>数据权限由 {@code DataPermissionInnerInterceptor} 统一改写
 * （{@code fin_invoice} 有 {@code owner_user_id}/{@code dept_id}），本类不手写。
 * 唯一需要显式绕过的两处（累计开票汇总、展示型补值）在各自 mapper 上有注释说明。
 */
@Slf4j
@Service
public class InvoiceServiceImpl implements InvoiceService {

    /** 导出文件在 OSS 上的业务目录 */
    private static final String EXPORT_BIZ_TYPE = "export";

    /** xlsx 的 Content-Type */
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** SpreadsheetML（.xml）的 Content-Type */
    private static final String XML_CONTENT_TYPE = "application/vnd.ms-excel";

    /** 结算用小数位：金额一律两位 */
    private static final int SCALE = 2;

    /** 回款可开票的状态口径：审核已通过 + 已确认收款 */
    private static final int PAYMENT_AUDIT_PASSED = 2;
    private static final int PAYMENT_CONFIRMED = 1;

    // 发票自身的状态取值（fin_invoice.audit_status / fin_invoice.status）
    /** 审核状态：待提审 —— 新建后的初始状态 */
    private static final int AUDIT_PENDING_SUBMIT = 0;
    /** 审核状态：审批中 */
    private static final int AUDIT_IN_PROGRESS = 1;
    /** 审核状态：已通过 —— 只有它能轮到财务开票或拒绝 */
    private static final int AUDIT_PASSED = 2;
    /** 审核状态：已驳回 */
    private static final int AUDIT_REJECTED = 3;
    /** 单据状态：待开票 */
    private static final int STATUS_PENDING_ISSUE = 0;
    /** 单据状态：已开票 */
    private static final int STATUS_ISSUED = 1;
    /** 单据状态：已作废 —— 额度靠它释放，见 {@code InvoicePaymentMapper#sumAllocatedByPaymentIds} */
    private static final int STATUS_VOIDED = 2;

    // 列表胶囊（scope）取值，与 PaymentServiceImpl / QuotationServiceImpl 保持同一套字面量
    private static final String SCOPE_ALL = "all";
    private static final String SCOPE_MINE = "mine";
    private static final String SCOPE_SUBORDINATE = "subordinate";
    private static final String SCOPE_STARRED = "starred";

    @Resource private InvoiceMapper invoiceMapper;
    @Resource private InvoicePaymentMapper invoicePaymentMapper;
    @Resource private PaymentMapper paymentMapper;
    @Resource private CustomerInvoiceInfoMiniMapper customerInvoiceInfoMiniMapper;
    @Resource private CrmContractMiniMapper crmContractMiniMapper;
    @Resource private CrmCustomerMiniMapper crmCustomerMiniMapper;
    @Resource private SysUserMiniMapper sysUserMiniMapper;
    @Resource private SubordinateUserIds subordinateUserIds;
    @Resource private InvoiceConvert invoiceConvert;
    @Resource private StarService starService;
    @Resource private OssStorageService ossStorageService;
    @Resource private AttachmentMapper attachmentMapper;
    @Resource private FinanceNumberService financeNumberService;

    // ==================== 查询 ====================

    @Override
    public PageResult<InvoiceRespVO> pageQuery(InvoicePageReqVO reqVO) {
        LambdaQueryWrapper<InvoiceDO> wrapper = buildWrapper(reqVO);
        // 胶囊口径（scope）在数据权限之内再收窄；财务开票页（auditPage）前端不传 scope，故天然走 all。
        if (!applyScope(wrapper, reqVO.getScope())) {
            return emptyResult(reqVO);
        }
        wrapper.orderByDesc(InvoiceDO::getCreateTime).orderByDesc(InvoiceDO::getInvoiceId);
        Page<InvoiceDO> page = invoiceMapper.selectPage(reqVO, wrapper);
        List<InvoiceRespVO> list = page.getRecords().stream()
                .map(invoiceConvert::toRespVO)
                .collect(Collectors.toList());
        // 列表也带上明细：财务开票页要逐行核对「这张票开了哪几笔回款」，
        // 没有它就得一行一个详情请求。一次 IN 查询换掉 N+1。
        Map<Long, List<InvoicePaymentRespVO>> allocations = loadAllocations(
                list.stream().map(InvoiceRespVO::getId).toList());
        list.forEach(vo -> vo.setAllocations(allocations.getOrDefault(vo.getId(), List.of())));
        fillNames(list);
        // 批量查关注态，避免逐行查库；空页时 starredBizIds 返回空集
        Set<Long> starredIds = starService.starredBizIds(StarBizType.INVOICE,
                list.stream().map(InvoiceRespVO::getId).filter(Objects::nonNull).toList());
        list.forEach(vo -> vo.setStarred(starredIds.contains(vo.getId())));
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override public PageResult<InvoiceRespVO> auditPage(InvoicePageReqVO req) {
        req.setAuditStatus(2); req.setStatus(0);
        // 开票列页是「待我开票」的工作台，不是用户选口径的列表：忽略 scope，
        // 免得前端哪天顺手带上 capsule 参数，把整个待办列表筛空。
        req.setScope(null);
        return pageQuery(req);
    }

    @Override
    public InvoiceRespVO get(Long id) {
        InvoiceDO invoice = required(id);
        InvoiceRespVO vo = invoiceConvert.toRespVO(invoice);
        vo.setAllocations(loadAllocations(List.of(id)).getOrDefault(id, List.of()));
        // 编辑表单靠 customerId / ownerUserId 无法回显「客户名称 / 负责人」两个输入框，
        // 单条详情也必须补名
        fillNames(List.of(vo));
        vo.setStarred(starService.isStarred(StarBizType.INVOICE, id));
        return vo;
    }

    /**
     * 批量补 customerName / ownerName。
     *
     * <p>发票表只有 id 列，两个名称都从源表现查，不冗余存储 —— 客户或人员改名后
     * 历史发票跟着显示新名字，这是「引用」而非「快照」该有的行为
     * （与发票抬头那六个字段刻意相反，那些是快照、存下来就不跟着客户档案变）。
     *
     * <p>两处查询都绕过数据权限：{@code customerName} 用
     * {@link CrmCustomerMiniMapper#selectListNoPerm}；负责人走
     * {@code SysUserMiniMapper} 的普通查询（{@code sys_user} 不在
     * {@code SCOPED_TABLES} 白名单，本来就不会被追加条件）。
     * 补名是展示型读取，不该因「同事是负责人」而空着。
     */
    private void fillNames(List<InvoiceRespVO> list) {
        if (list.isEmpty()) {
            return;
        }
        List<Long> customerIds = list.stream().map(InvoiceRespVO::getCustomerId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> customerNames = customerIds.isEmpty() ? Map.of()
                : crmCustomerMiniMapper.selectListNoPerm(customerIds).stream()
                        .collect(Collectors.toMap(CrmCustomerMiniDO::getCustomerId,
                                CrmCustomerMiniDO::getCustomerName, (a, b) -> a));
        List<Long> ownerIds = list.stream().map(InvoiceRespVO::getOwnerUserId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> ownerNames = ownerIds.isEmpty() ? Map.of()
                : sysUserMiniMapper.selectList(SysUserMiniDO::getUserId, ownerIds).stream()
                        .collect(Collectors.toMap(SysUserMiniDO::getUserId,
                                SysUserMiniDO::getRealName, (a, b) -> a));
        for (InvoiceRespVO vo : list) {
            vo.setCustomerName(customerNames.get(vo.getCustomerId()));
            vo.setOwnerName(ownerNames.get(vo.getOwnerUserId()));
        }
    }

    /**
     * 「新增/编辑发票登记」表单选好客户后一次拿全：默认开票信息 + 该客户的可开票回款。
     *
     * <p>两段查询的权限语义<b>刻意不同</b>，不能图省事合成一条忽略权限的 SQL：
     * <ol>
     *   <li>开票信息：客源表不在数据权限白名单，正常查即可</li>
     *   <li>候选回款：走 {@code fin_payment} 的普通查询，<b>由拦截器按当前登录人的
     *       数据范围收窄</b>——不能给同事负责的回款开票</li>
     * </ol>
     *
     * @param invoiceId 编辑态传本发票 ID，用于把它自己的占用从「已开票额」里排除：
     *                  被本发票占满的回款在编辑时仍应可见可改。新增时传 null。
     */
    @Override
    public InvoiceCustomerFormRespVO customerForm(Long customerId, Long invoiceId) {
        if (customerId == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "请先选择客户");
        }
        // invoiceId 会被当作「排除自身占用」的入参喂给 ownAllocated()，而那条查询
        // 按 invoice_id 直查 fin_invoice_payment、不带数据权限。不在这里先校验归属，
        // 传一个别人的发票ID就能读出「那张票对某笔回款开了多少钱」。
        if (invoiceId != null) {
            required(invoiceId);
        }
        InvoiceCustomerFormRespVO resp = new InvoiceCustomerFormRespVO();
        CrmCustomerInvoiceInfoMiniDO info = customerInvoiceInfoMiniMapper.selectDefaultByCustomerId(customerId);
        if (info != null) {
            CustomerInvoiceInfoVO vo = new CustomerInvoiceInfoVO();
            vo.setInvoiceTitle(info.getInvoiceTitle());
            vo.setTaxNo(info.getTaxNo());
            vo.setBankName(info.getBankName());
            vo.setBankAccount(info.getBankAccount());
            vo.setInvoiceAddr(info.getInvoiceAddr());
            vo.setInvoicePhone(info.getInvoicePhone());
            resp.setInvoiceInfo(vo);
        }
        resp.setPayments(listInvoiceablePayments(customerId, invoiceId));
        return resp;
    }

    /** 该客户当下仍可开票的回款（剩余可开额 > 0），含编辑态本发票已占用的行 */
    private List<InvoiceablePaymentVO> listInvoiceablePayments(Long customerId, Long invoiceId) {
        LambdaQueryWrapper<PaymentDO> w = Wrappers.lambdaQuery();
        // 数据权限拦截器会在此基础上再追加归属范围条件
        w.eq(PaymentDO::getCustomerId, customerId)
                .eq(PaymentDO::getAuditStatus, PAYMENT_AUDIT_PASSED)
                .eq(PaymentDO::getStatus, PAYMENT_CONFIRMED)
                .orderByDesc(PaymentDO::getPaymentDate)
                .orderByDesc(PaymentDO::getPaymentId);
        List<PaymentDO> payments = paymentMapper.selectList(w);
        if (payments.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> allocated = allocatedByPayment(
                payments.stream().map(PaymentDO::getPaymentId).toList(), invoiceId);
        // 编辑态：本发票已对哪些回款开了多少，用于回显对应行并把额度加回去
        Map<Long, BigDecimal> own = invoiceId == null ? Map.of() : ownAllocated(invoiceId);
        List<Long> contractIds = payments.stream().map(PaymentDO::getContractId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, CrmContractMiniDO> contracts = contractIds.isEmpty() ? Map.of()
                : crmContractMiniMapper.selectListNoPerm(contractIds).stream()
                        .collect(Collectors.toMap(CrmContractMiniDO::getContractId, c -> c, (a, b) -> a));
        List<InvoiceablePaymentVO> result = new ArrayList<>();
        for (PaymentDO p : payments) {
            BigDecimal amount = money(p.getAmount());
            BigDecimal mine = own.getOrDefault(p.getPaymentId(), BigDecimal.ZERO);
            BigDecimal others = allocated.getOrDefault(p.getPaymentId(), BigDecimal.ZERO);
            // 可开余额 = 回款金额 - 他人占用；本发票自己的占用不算「被占」
            BigDecimal remaining = amount.subtract(others).max(BigDecimal.ZERO);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            InvoiceablePaymentVO vo = new InvoiceablePaymentVO();
            vo.setPaymentId(p.getPaymentId());
            vo.setPaymentNo(p.getPaymentNo());
            vo.setContractId(p.getContractId());
            vo.setPaymentMethod(p.getPaymentMethod());
            vo.setPaymentMethodName(FinanceDictLabels.paymentMethod(p.getPaymentMethod()));
            vo.setPaymentDate(p.getPaymentDate());
            vo.setAmount(amount);
            vo.setAllocatedAmount(money(others));
            vo.setRemainingAmount(remaining);
            vo.setWriteOffAmount(mine);
            CrmContractMiniDO contract = p.getContractId() == null ? null : contracts.get(p.getContractId());
            if (contract != null) {
                vo.setContractNo(contract.getContractNo());
                vo.setContractName(contract.getContractName());
            }
            result.add(vo);
        }
        return result;
    }

    // ==================== 写 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(InvoiceSaveReqVO req) {
        Resolved resolved = resolve(req, null);
        InvoiceDO invoice = new InvoiceDO();
        invoice.setInvoiceId(IdUtil.getSnowflakeNextId());
        Map<String, Object> numberContext = new HashMap<>();
        numberContext.put("customerId", resolved.customerId());
        if (LoginUserHolder.getDeptId() != null) numberContext.put("deptId", LoginUserHolder.getDeptId());
        invoice.setApplyNo(financeNumberService.nextInvoiceNumber(numberContext));
        applyFields(invoice, req, resolved);
        LoginUser user = LoginUserHolder.getRequired();
        invoice.setOwnerUserId(user.getUserId());
        invoice.setDeptId(user.getDeptId());
        invoice.setAuditStatus(0);
        invoice.setStatus(0);
        invoiceMapper.insert(invoice);
        insertAllocations(invoice.getInvoiceId(), resolved.items());
        return invoice.getInvoiceId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, InvoiceSaveReqVO req) {
        InvoiceDO old = required(id);
        editable(old);
        // 校验时排除本发票自身占用，「原样保存」才不会被自己的明细判成超余额
        Resolved resolved = resolve(req, id);
        InvoiceDO update = new InvoiceDO();
        update.setInvoiceId(id);
        applyFields(update, req, resolved);
        // 必须用 set(...) 显式列名而非 updateById(new DO)：
        // application.yml 的 update-strategy=not_null 会跳过 null 字段，
        // 混合同发票要把 contract_id / payment_id 置空时就做不到，旧的脏值会留下。
        // 另外此处刻意不碰 applyNo / ownerUserId / deptId / auditStatus / status / issueDate。
        LambdaUpdateWrapper<InvoiceDO> w = Wrappers.lambdaUpdate();
        w.eq(InvoiceDO::getInvoiceId, id)
                // 状态一并下推。editable(old) 只是读检查，从读到写之间有窗口：
                // 别人提交、财务随即开票之后，下面这个 UPDATE 仍会照常执行，
                // 把一张「发票号已经印在纸质票上」的发票改掉金额。
                .in(InvoiceDO::getAuditStatus, AUDIT_PENDING_SUBMIT, AUDIT_REJECTED)
                .ne(InvoiceDO::getStatus, STATUS_VOIDED)
                .set(InvoiceDO::getPaymentId, update.getPaymentId())
                .set(InvoiceDO::getContractId, update.getContractId())
                .set(InvoiceDO::getCustomerId, update.getCustomerId())
                .set(InvoiceDO::getInvoiceType, update.getInvoiceType())
                .set(InvoiceDO::getInvoiceTitle, update.getInvoiceTitle())
                .set(InvoiceDO::getTaxNo, update.getTaxNo())
                .set(InvoiceDO::getBankName, update.getBankName())
                .set(InvoiceDO::getBankAccount, update.getBankAccount())
                .set(InvoiceDO::getInvoiceAddr, update.getInvoiceAddr())
                .set(InvoiceDO::getInvoicePhone, update.getInvoicePhone())
                .set(InvoiceDO::getAmount, update.getAmount())
                .set(InvoiceDO::getTaxRate, update.getTaxRate())
                .set(InvoiceDO::getTaxAmount, update.getTaxAmount())
                .set(InvoiceDO::getTotalAmount, update.getTotalAmount())
                .set(InvoiceDO::getApplyDate, update.getApplyDate())
                .set(InvoiceDO::getRemark, update.getRemark())
                .set(InvoiceDO::getAttachmentIds, update.getAttachmentIds());
        // ⚠️ 第一个参数必须传实体（哪怕是个空壳），<b>不能传 null</b>：
        //    MyBatis-Plus 的 updateFill 只在入参是实体时触发，传 null 会让
        //    update_time / update_by 静默不更新 —— 改了金额却看不出修改时间。
        //    业务字段全部走 wrapper.set()（要的就是它能写 null），实体只负责
        //    承接审计字段的自动填充。
        requireClaimed(invoiceMapper.update(new InvoiceDO(), w),
                "该发票已被处理，请刷新后重试");
        // 明细整批重建：本表无 del_flag，BaseMapper.delete(wrapper) 发的是物理 DELETE
        invoicePaymentMapper.delete(Wrappers.lambdaQuery(InvoicePaymentDO.class)
                .eq(InvoicePaymentDO::getInvoiceId, id));
        insertAllocations(id, resolved.items());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        editable(required(id));
        // 先物理删明细再软删父单：留着孤儿明细会让「累计已开票」把已删发票继续算进去
        invoicePaymentMapper.delete(Wrappers.lambdaQuery(InvoicePaymentDO.class)
                .eq(InvoicePaymentDO::getInvoiceId, id));
        invoiceMapper.deleteById(id);
    }

    /**
     * 提交审核。
     *
     * <p>{@code editable(old)} 负责给出准确的提示语（「审核中或已开票不允许编辑」），
     * 但它只是读检查，<b>不参与并发判定</b> —— 真正的裁决权在下面带条件的 UPDATE。
     * 与 {@code PaymentServiceImpl#audit} 的 {@code claimed == 0} 是同一手法。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        editable(required(id));
        requireClaimed(invoiceMapper.update(new InvoiceDO(), Wrappers.<InvoiceDO>lambdaUpdate()
                .eq(InvoiceDO::getInvoiceId, id)
                .in(InvoiceDO::getAuditStatus, AUDIT_PENDING_SUBMIT, AUDIT_REJECTED)
                .ne(InvoiceDO::getStatus, STATUS_VOIDED)
                .set(InvoiceDO::getAuditStatus, AUDIT_IN_PROGRESS)),
                "该发票已被处理，请刷新后重试");
    }

    /**
     * 财务开票。
     *
     * <p><b>为什么必须条件更新</b>：开票与 {@link #reject} 是同一个待办页上的两个按钮。
     * 两人同时点（或一个人双击两个按钮）时，双方都会通过上面的读检查，然后：
     * 拒绝那条把 {@code audit_status} 改成 3，开票这条把 {@code status} 改成 1 并回填
     * 发票号 —— 两者更新的列不重叠，谁都覆盖不了谁，最终落库成
     * 「<b>已驳回 + 已开票</b>」的自相矛盾单据。而额度口径是 {@code status != 2}，
     * 这张被拒绝的票会永久占住回款额度，同时又出现在已开票的统计里。
     *
     * <p>把判断下推成 {@code WHERE audit_status = 2 AND status = 0} 之后，
     * 后到的那条影响行数为 0，直接失败。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void issue(Long id, InvoiceIssueReqVO req) {
        InvoiceDO old = required(id);
        if (!Integer.valueOf(AUDIT_PASSED).equals(old.getAuditStatus())
                || !Integer.valueOf(STATUS_PENDING_ISSUE).equals(old.getStatus())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "该发票当前不可开票");
        }
        // apply_date 建表是 NOT NULL，这里的判空纯属防御：真出现脏数据时，
        // 让校验跳过也比抛 NPE 变成 500 要好 —— 用户至少能看懂发生了什么。
        if (old.getApplyDate() != null && req.getIssueDate().isBefore(old.getApplyDate())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "开票日期不能早于申请日期");
        }
        if (req.getAttachmentIds().stream().anyMatch(x -> attachmentMapper.selectById(x) == null)) {
            throw new ServiceException(ErrorCode.ATTACHMENT_NOT_EXISTS);
        }
        requireClaimed(invoiceMapper.update(new InvoiceDO(), Wrappers.<InvoiceDO>lambdaUpdate()
                .eq(InvoiceDO::getInvoiceId, id)
                .eq(InvoiceDO::getAuditStatus, AUDIT_PASSED)
                .eq(InvoiceDO::getStatus, STATUS_PENDING_ISSUE)
                .set(InvoiceDO::getInvoiceNo, req.getInvoiceNo().trim())
                .set(InvoiceDO::getIssueDate, req.getIssueDate())
                .set(InvoiceDO::getIssueUserId, LoginUserHolder.getUserId())
                .set(InvoiceDO::getAttachmentIds, JSONUtil.toJsonStr(req.getAttachmentIds()))
                .set(InvoiceDO::getStatus, STATUS_ISSUED)),
                "该发票已被处理，请刷新后重试");
    }

    /**
     * 财务拒绝开票。
     *
     * <p>前置条件与 {@link #issue} 完全对称：只有「已通过 + 待开票」的单子才轮得到财务
     * 处置，否则说明它根本不在财务的待办里。落在 {@code auditStatus = 3 已驳回}、
     * {@code status} 保持 0。
     *
     * <p><b>不删明细</b>：与作废不同，拒绝只是把单子退回申请人返工，被占用的回款开票额度
     * 应当继续占着 —— 否则申请人重新提交前，同一笔回款会被别人再开一张票，
     * 两张票合计超开。要释放额度应走删除发票。
     *
     * <p>⚠️ {@code comment} <b>只校验、不落库</b>：{@code fin_invoice} 没有审核意见列，
     * 与回款审核（{@code PaymentServiceImpl#audit}）现状一致。要真正留存需先给
     * {@code fin_invoice} 加列，届时回款那边也该一起补。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, InvoiceRejectReqVO req) {
        InvoiceDO old = required(id);
        if (!Integer.valueOf(AUDIT_PASSED).equals(old.getAuditStatus())
                || !Integer.valueOf(STATUS_PENDING_ISSUE).equals(old.getStatus())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "该发票当前不可拒绝");
        }
        if (!StringUtils.hasText(req.getComment())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "拒绝时必须填写拒绝原因");
        }
        // 与 issue() 对称：两者争的是同一张「已通过 + 待开票」的单子，
        // 谁先把状态改掉谁生效，后到的影响行数为 0（见 issue 的注释）。
        requireClaimed(invoiceMapper.update(new InvoiceDO(), Wrappers.<InvoiceDO>lambdaUpdate()
                .eq(InvoiceDO::getInvoiceId, id)
                .eq(InvoiceDO::getAuditStatus, AUDIT_PASSED)
                .eq(InvoiceDO::getStatus, STATUS_PENDING_ISSUE)
                .set(InvoiceDO::getAuditStatus, AUDIT_REJECTED)),
                "该发票已被处理，请刷新后重试");
    }

    /**
     * 作废发票。
     *
     * <p>⚠️ <b>刻意不删明细</b>：作废保留核销轨迹供审计。它占用的额度靠所有汇总
     * SQL 里的 {@code fin_invoice.status != 2} 口径释放 —— 见
     * {@code InvoicePaymentMapper#sumAllocatedByPaymentIds}。在这里删明细虽能让
     * 额度立刻回来，但会让「这张票当初开了哪些回款」永久丢失。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidInvoice(Long id) {
        InvoiceDO old = required(id);
        if (!Integer.valueOf(STATUS_ISSUED).equals(old.getStatus())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "只有已开票发票可以作废");
        }
        // 作废会把这张票占用的回款额度释放掉（汇总口径是 status != 2），
        // 重复执行的后果是额度被释放两次的判断依据出错，所以同样下推状态。
        requireClaimed(invoiceMapper.update(new InvoiceDO(), Wrappers.<InvoiceDO>lambdaUpdate()
                .eq(InvoiceDO::getInvoiceId, id)
                .eq(InvoiceDO::getStatus, STATUS_ISSUED)
                .set(InvoiceDO::getStatus, STATUS_VOIDED)),
                "该发票已被处理，请刷新后重试");
    }

    /**
     * 关注 / 取消关注。
     *
     * <p>与 {@code PaymentServiceImpl#star} 同一个做法：先 {@code required(id)}
     * 确认单据存在（且受数据权限约束 —— 不能关注自己看不到的发票），
     * 再写通用关注表。{@code crm_star} 的 biz_type 用 {@code StarBizType.INVOICE = 7}，
     * 该常量早已登记，不需要新增。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void star(Long id) {
        required(id);
        starService.star(StarBizType.INVOICE, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unstar(Long id) {
        required(id);
        starService.unstar(StarBizType.INVOICE, id);
    }

    // ==================== 校验与算数 ====================

    /** 校验通过的明细 + 推导出的客户 / 合同 / 金额，供 create/update 共用 */
    private record Resolved(List<Item> items, Long customerId, Long contractId,
                            BigDecimal amount, BigDecimal taxAmount, BigDecimal totalAmount) {
    }

    /** 明细行：回款 ID 与本行含税开票金额 */
    private record Item(Long paymentId, BigDecimal writeOffAmount) {
    }

    /**
     * 校验请求并推导父单字段（create / update 共用一份规则）。
     *
     * @param invoiceId 编辑时的发票ID，用于把本发票已有占用从累计校验里排除；新增传 null
     */
    private Resolved resolve(InvoiceSaveReqVO req, Long invoiceId) {
        List<InvoicePaymentReqVO> reqItems = req.getItems();
        if (reqItems == null || reqItems.isEmpty()) {
            throw new ServiceException(ErrorCode.INVOICE_PAYMENT_REQUIRED);
        }
        validateTaxRate(req.getTaxRate());

        // 1) 先去重。放到最前面是因为 uk_inv_pay 会抛 DuplicateKeyException，
        //    用户看到的将是 500 而不是 400 —— 得在落库前自己拦住。
        Map<Long, BigDecimal> unique = new LinkedHashMap<>();
        for (InvoicePaymentReqVO item : reqItems) {
            if (item.getPaymentId() == null) {
                throw new ServiceException(ErrorCode.INVOICE_PAYMENT_INVALID);
            }
            BigDecimal amount = money(item.getWriteOffAmount());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ServiceException(ErrorCode.INVOICE_AMOUNT_INVALID);
            }
            if (unique.putIfAbsent(item.getPaymentId(), amount) != null) {
                throw new ServiceException(ErrorCode.INVOICE_PAYMENT_DUPLICATE);
            }
        }
        List<Long> paymentIds = List.copyOf(unique.keySet());

        // 2) 加锁读回款。不加 @InterceptorIgnore：可见性检查与加锁读合一，
        //    用户不能给自己看不到的回款开票。ORDER BY payment_id 规避 AB/BA 死锁。
        List<PaymentDO> payments = paymentMapper.selectByIdsForUpdate(paymentIds);
        if (payments.size() != paymentIds.size()) {
            throw new ServiceException(ErrorCode.INVOICE_PAYMENT_INVALID);
        }

        // 3) 同一客户。发票的客户由此确定 —— 请求体里没有 customerId
        Set<Long> customerIds = payments.stream().map(PaymentDO::getCustomerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (customerIds.size() != 1) {
            throw new ServiceException(ErrorCode.INVOICE_PAYMENT_CUSTOMER_MISMATCH);
        }
        Long customerId = customerIds.iterator().next();

        // 4) 逐笔：状态可开票 + 累计不超
        Map<Long, PaymentDO> byId = payments.stream()
                .collect(Collectors.toMap(PaymentDO::getPaymentId, Function.identity()));
        Map<Long, BigDecimal> allocated = allocatedByPayment(paymentIds, invoiceId);
        for (Map.Entry<Long, BigDecimal> e : unique.entrySet()) {
            PaymentDO p = byId.get(e.getKey());
            if (!Integer.valueOf(PAYMENT_AUDIT_PASSED).equals(p.getAuditStatus())
                    || !Integer.valueOf(PAYMENT_CONFIRMED).equals(p.getStatus())) {
                throw new ServiceException(ErrorCode.INVOICE_PAYMENT_INVALID);
            }
            BigDecimal occupied = allocated.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal usable = money(p.getAmount()).subtract(occupied);
            if (e.getValue().compareTo(usable) > 0) {
                throw new ServiceException(ErrorCode.INVOICE_AMOUNT_EXCEED_PAYMENT,
                        "回款 " + p.getPaymentNo() + " 的可开票余额不足，还可开票 " + usable + " 元");
            }
        }

        // 5) 金额：先逐行四舍五入，再汇总
        List<Item> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> e : unique.entrySet()) {
            items.add(new Item(e.getKey(), e.getValue()));
            total = total.add(e.getValue());
        }
        BigDecimal[] split = splitTax(total, req.getTaxRate());

        // 6) 合同：所有明细同属一份合同时才写，否则留 NULL。
        //    混合时若存「第一条」，合同详情页的发票 Tab 会静默漏掉其余合同 ——
        //    那是个看起来像数据的谎话，NULL 反而诚实。
        Set<Long> contractIds = payments.stream().map(PaymentDO::getContractId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Long contractId = contractIds.size() == 1 ? contractIds.iterator().next() : null;

        return new Resolved(items, customerId, contractId, split[0], split[1], total);
    }

    /**
     * 按含税总额与税率拆出不含税金额与税额。
     *
     * <p>先逐行四舍五入再汇总，最后取<b>残差</b>作为税额（而不是重算
     * {@code amount × rate}）—— 只有这样才能保证
     * {@code total = amount + tax_amount} 精确成立，即
     * {@code docs/02-建表文档.md} 要求的「明细与合计对得上」。
     * 单行时与直接换算等价。
     */
    private BigDecimal[] splitTax(BigDecimal total, BigDecimal taxRate) {
        BigDecimal noTax = total
                .divide(BigDecimal.ONE.add(taxRate.movePointLeft(2)), SCALE, RoundingMode.HALF_UP);
        return new BigDecimal[]{noTax, total.subtract(noTax)};
    }

    private void validateTaxRate(BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) < 0
                || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ServiceException(ErrorCode.INVOICE_TAX_RATE_INVALID);
        }
    }

    /** 把推导结果与请求字段铺到父单上（create 用新对象，update 用 set 包装器） */
    private void applyFields(InvoiceDO v, InvoiceSaveReqVO req, Resolved resolved) {
        List<Item> items = resolved.items();
        // 主回款 = 第一条明细的回款，按请求顺序。仅留给 legacy 展示与 idx_payment_id
        v.setPaymentId(items.get(0).paymentId());
        v.setContractId(resolved.contractId());
        v.setCustomerId(resolved.customerId());
        v.setInvoiceType(req.getInvoiceType());
        v.setInvoiceTitle(req.getInvoiceTitle());
        v.setTaxNo(req.getTaxNo());
        v.setBankName(req.getBankName());
        v.setBankAccount(req.getBankAccount());
        v.setInvoiceAddr(req.getInvoiceAddr());
        v.setInvoicePhone(req.getInvoicePhone());
        v.setTaxRate(req.getTaxRate());
        v.setTotalAmount(resolved.totalAmount());
        v.setAmount(resolved.amount());
        v.setTaxAmount(resolved.taxAmount());
        v.setApplyDate(req.getApplyDate());
        v.setRemark(req.getRemark());
        v.setAttachmentIds(req.getAttachmentIds() == null ? null : JSONUtil.toJsonStr(req.getAttachmentIds()));
    }

    /**
     * 写入明细。
     *
     * <p>{@code createBy}/{@code createTime} 必须显式赋值：{@link InvoicePaymentDO}
     * 不继承 {@code BaseDO}（本表没有 update_* / del_flag），没有
     * {@code @TableField(fill = ...)}，MyBatis-Plus 的 insertFill 不会触发，
     * 不手动设就落库为 NULL。
     */
    private void insertAllocations(Long invoiceId, List<Item> items) {
        LoginUser user = LoginUserHolder.getRequired();
        LocalDateTime now = LocalDateTime.now();
        for (Item item : items) {
            InvoicePaymentDO row = new InvoicePaymentDO();
            row.setId(IdUtil.getSnowflakeNextId());
            row.setInvoiceId(invoiceId);
            row.setPaymentId(item.paymentId());
            row.setWriteOffAmount(item.writeOffAmount());
            row.setCreateBy(user.getUserId());
            row.setCreateTime(now);
            invoicePaymentMapper.insert(row);
        }
    }

    /**
     * 批量汇总各回款「已被开票多少」。
     *
     * <p>只有一处口径（{@link InvoicePaymentMapper#sumAllocatedByPaymentIds}），
     * 别在 Java 里手搓第二遍 —— 那样「作废发票是否释放额度」之类的规则会分裂成两份。
     */
    private Map<Long, BigDecimal> allocatedByPayment(Collection<Long> paymentIds, Long excludeInvoiceId) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            return Map.of();
        }
        List<PaymentAllocatedVO> rows = invoicePaymentMapper.sumAllocatedByPaymentIds(paymentIds, excludeInvoiceId);
        Map<Long, BigDecimal> result = new HashMap<>();
        for (PaymentAllocatedVO row : rows) {
            result.put(row.getPaymentId(), money(row.getAllocatedAmount()));
        }
        return result;
    }

    /** 本发票对每笔回款已开票的金额（编辑态回显用） */
    private Map<Long, BigDecimal> ownAllocated(Long invoiceId) {
        List<InvoicePaymentDO> rows = invoicePaymentMapper.selectList(
                Wrappers.lambdaQuery(InvoicePaymentDO.class)
                        .eq(InvoicePaymentDO::getInvoiceId, invoiceId));
        Map<Long, BigDecimal> result = new HashMap<>();
        for (InvoicePaymentDO row : rows) {
            result.put(row.getPaymentId(), money(row.getWriteOffAmount()));
        }
        return result;
    }

    /** 按发票ID批量装明细（含回款方的补值字段），供列表/详情用 */
    private Map<Long, List<InvoicePaymentRespVO>> loadAllocations(Collection<Long> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            return Map.of();
        }
        List<InvoicePaymentDO> rows = invoicePaymentMapper.selectList(
                Wrappers.lambdaQuery(InvoicePaymentDO.class)
                        .in(InvoicePaymentDO::getInvoiceId, invoiceIds)
                        .orderByAsc(InvoicePaymentDO::getId));
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> paymentIds = rows.stream().map(InvoicePaymentDO::getPaymentId).distinct().toList();
        // 展示型补值走绕过数据权限的版本：同事名下的回款也要显示编号，
        // 否则编辑表单里那一行看起来像数据丢了
        Map<Long, PaymentDO> payments = paymentIds.isEmpty() ? Map.of()
                : paymentMapper.selectByIdsNoPerm(paymentIds).stream()
                        .collect(Collectors.toMap(PaymentDO::getPaymentId, Function.identity(), (a, b) -> a));
        Map<Long, List<InvoicePaymentRespVO>> grouped = new HashMap<>();
        for (InvoicePaymentDO row : rows) {
            InvoicePaymentRespVO vo = new InvoicePaymentRespVO();
            vo.setId(row.getId());
            vo.setPaymentId(row.getPaymentId());
            vo.setWriteOffAmount(money(row.getWriteOffAmount()));
            PaymentDO p = payments.get(row.getPaymentId());
            if (p != null) {
                vo.setPaymentNo(p.getPaymentNo());
                vo.setPaymentMethod(p.getPaymentMethod());
                vo.setPaymentMethodName(FinanceDictLabels.paymentMethod(p.getPaymentMethod()));
                vo.setPaymentDate(p.getPaymentDate());
                vo.setPaymentAmount(money(p.getAmount()));
            }
            grouped.computeIfAbsent(row.getInvoiceId(), k -> new ArrayList<>()).add(vo);
        }
        return grouped;
    }

    // ==================== 导出 ====================

    @Override
    public String export(InvoicePageReqVO reqVO) {
        byte[] bytes = ExcelKit.write(exportRows(reqVO), InvoiceExportRowVO.class, "发票");
        String objectKey = ossStorageService.upload(bytes, EXPORT_BIZ_TYPE, XLSX_CONTENT_TYPE, "xlsx");
        return ossStorageService.buildUrl(objectKey);
    }

    @Override
    public String exportXml(InvoicePageReqVO reqVO) {
        byte[] bytes = SpreadsheetXml.write(exportRows(reqVO), InvoiceExportRowVO.class, "发票");
        String objectKey = ossStorageService.upload(bytes, EXPORT_BIZ_TYPE, XML_CONTENT_TYPE, "xml");
        return ossStorageService.buildUrl(objectKey);
    }

    private List<InvoiceExportRowVO> exportRows(InvoicePageReqVO reqVO) {
        LambdaQueryWrapper<InvoiceDO> wrapper = buildWrapper(reqVO);
        // 导出复用列表的胶囊口径（scope）；数据权限仍由拦截器兜底
        if (!applyScope(wrapper, reqVO.getScope())) {
            return List.of();
        }
        wrapper.orderByDesc(InvoiceDO::getCreateTime);
        return invoiceMapper.selectList(wrapper).stream().map(this::toExportRow).collect(Collectors.toList());
    }

    /** 空页。{@code scope} 收窄到空集时用它，避免把「无数据」误报成「不筛」。 */
    private PageResult<InvoiceRespVO> emptyResult(PageParam reqVO) {
        Page<?> page = reqVO.toPage();
        return PageResult.empty((int) page.getCurrent(), (int) page.getSize());
    }

    // ==================== 内部方法 ====================

    private LambdaQueryWrapper<InvoiceDO> buildWrapper(InvoicePageReqVO reqVO) {
        LambdaQueryWrapper<InvoiceDO> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(reqVO.getKeyword())) {
            String keyword = reqVO.getKeyword().trim();
            wrapper.and(w -> w.like(InvoiceDO::getApplyNo, keyword)
                    .or().like(InvoiceDO::getInvoiceNo, keyword));
        }
        if (StringUtils.hasText(reqVO.getApplyNo())) {
            wrapper.like(InvoiceDO::getApplyNo, reqVO.getApplyNo());
        }
        if (StringUtils.hasText(reqVO.getInvoiceNo())) {
            wrapper.like(InvoiceDO::getInvoiceNo, reqVO.getInvoiceNo());
        }
        if (reqVO.getCustomerId() != null) {
            wrapper.eq(InvoiceDO::getCustomerId, reqVO.getCustomerId());
        }
        if (reqVO.getContractId() != null) {
            wrapper.eq(InvoiceDO::getContractId, reqVO.getContractId());
        }
        // 按回款过滤必须查子表。fin_invoice.payment_id 只存第一条明细，
        // 直接 eq 会漏掉「该回款是第 2 行以后」的发票 —— 静默丢数据。
        // 外层无别名，子查询里用物理表名；子表不在数据权限白名单，拦截器不会碰它。
        if (reqVO.getPaymentId() != null) {
            wrapper.exists("SELECT 1 FROM fin_invoice_payment ip "
                    + "WHERE ip.invoice_id = fin_invoice.invoice_id AND ip.payment_id = {0}",
                    reqVO.getPaymentId());
        }
        if (reqVO.getOwnerUserId() != null) {
            wrapper.eq(InvoiceDO::getOwnerUserId, reqVO.getOwnerUserId());
        }
        if (reqVO.getAuditStatus() != null) {
            wrapper.eq(InvoiceDO::getAuditStatus, reqVO.getAuditStatus());
        }
        if (reqVO.getStatus() != null) {
            wrapper.eq(InvoiceDO::getStatus, reqVO.getStatus());
        }
        return wrapper;
    }

    /**
     * 胶囊口径过滤（{@code scope}），与数据权限拦截器是<b>交集</b>关系 ——
     * 用户主动选的取数口径只能在数据权限允许的范围内继续收窄，绝不会放宽。
     *
     * <p>与 {@link PaymentServiceImpl} 同一套语义与写法；状态类胶囊
     * （draft / issuing / issued / rejected）由前端先映射成 {@code status}/{@code auditStatus}，
     * 到这里只剩三个数据范围取值。
     *
     * <p>只追加裸的 {@code eq} / {@code in} 连接词，绝不追加裸 {@code or}。
     *
     * @return {@code false} 表示结果必然为空，调用方应直接返回空页
     */
    private boolean applyScope(LambdaQueryWrapper<InvoiceDO> wrapper, String scope) {
        if (!StringUtils.hasText(scope) || SCOPE_ALL.equals(scope)) {
            return true;
        }
        LoginUser me = LoginUserHolder.getRequired();
        switch (scope) {
            case SCOPE_MINE -> {
                if (me.getUserId() == null) {
                    return false;
                }
                wrapper.eq(InvoiceDO::getOwnerUserId, me.getUserId());
            }
            case SCOPE_SUBORDINATE -> {
                // 与回款/商机模块同一取舍：按上级链而非部门树 —— 部门会把同部门同事
                // 甚至我自己一起带出来，那不是「我的下属」。
                List<Long> subordinateIds = subordinateUserIds.of(me.getUserId());
                if (subordinateIds.isEmpty()) {
                    return false;
                }
                wrapper.in(InvoiceDO::getOwnerUserId, subordinateIds);
            }
            case SCOPE_STARRED -> {
                // 关注是跨归属的，所以按发票 id 过滤，而不是 owner
                Set<Long> starredIds = starService.starredBizIds(StarBizType.INVOICE);
                if (starredIds.isEmpty()) {
                    return false;
                }
                wrapper.in(InvoiceDO::getInvoiceId, starredIds);
            }
            // 未登记的取值不静默放行，直接 400 更容易发现前端漏配
            default -> throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "不支持的 scope 取值：" + scope);
        }
        return true;
    }

    /**
     * 状态流转的裁决点：条件 UPDATE 的影响行数为 0，说明单据已被别人改过。
     *
     * <p>本类所有会改变 {@code audit_status} / {@code status} 的写操作都走这里，
     * <b>一律不用 {@code updateById}</b> —— 它没有 CAS：从 {@link #required} 读到状态
     * 到落库之间存在窗口，两人的结论会互相覆盖，最终状态由后写者决定。
     * 这与 {@code PaymentServiceImpl#audit} 里 {@code claimed == 0} 是同一手法。
     *
     * <p>调用方通常仍会先做一次读检查（如 {@link #editable}），那是为了给出
     * 准确的提示语，<b>不参与并发判定</b> —— 读到的状态可能在读到之后就被改掉。
     */
    private void requireClaimed(int claimed, String message) {
        if (claimed == 0) {
            throw new ServiceException(ErrorCode.RESOURCE_ALREADY_TAKEN, message);
        }
    }

    private InvoiceDO required(Long id) { InvoiceDO v=invoiceMapper.selectById(id); if(v==null) throw new ServiceException(ErrorCode.INVOICE_NOT_EXISTS); return v; }

    /**
     * 可编辑 = 审核状态为「待提审 / 已驳回」且未作废。
     *
     * <p>⚠️ 这里的判断条件必须与 {@link #update} 里下推到 WHERE 的那两个条件保持一致，
     * 否则会出现「读检查放行、UPDATE 却影响 0 行」的假失败。
     */
    private void editable(InvoiceDO v) {
        boolean draftOrRejected = Integer.valueOf(AUDIT_PENDING_SUBMIT).equals(v.getAuditStatus())
                || Integer.valueOf(AUDIT_REJECTED).equals(v.getAuditStatus());
        if (!draftOrRejected) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "审核中或已开票发票不允许编辑");
        }
        if (Integer.valueOf(STATUS_VOIDED).equals(v.getStatus())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "已作废发票不允许编辑");
        }
    }

    /** 业务金额统一两位小数，避免前端传 3 位小数导致父子单据对不上 */
    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private InvoiceExportRowVO toExportRow(InvoiceDO inv) {
        InvoiceExportRowVO row = new InvoiceExportRowVO();
        row.setApplyNo(inv.getApplyNo());
        row.setInvoiceNo(inv.getInvoiceNo());
        // TODO 跨模块补名：finance 不依赖 crm，暂导出合同/客户原始 ID
        row.setContractId(inv.getContractId() == null ? "" : String.valueOf(inv.getContractId()));
        row.setCustomerId(inv.getCustomerId() == null ? "" : String.valueOf(inv.getCustomerId()));
        row.setInvoiceType(nullToEmpty(FinanceDictLabels.invoiceType(inv.getInvoiceType())));
        row.setAmount(inv.getAmount());
        row.setTaxAmount(inv.getTaxAmount());
        row.setTotalAmount(inv.getTotalAmount());
        row.setApplyDate(inv.getApplyDate());
        row.setAuditStatus(nullToEmpty(FinanceDictLabels.auditStatus(inv.getAuditStatus())));
        row.setStatus(nullToEmpty(FinanceDictLabels.invoiceStatus(inv.getStatus())));
        row.setCreateTime(inv.getCreateTime());
        return row;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
