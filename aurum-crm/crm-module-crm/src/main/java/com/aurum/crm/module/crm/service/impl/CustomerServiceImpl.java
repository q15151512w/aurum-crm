package com.aurum.crm.module.crm.service.impl;

import com.aurum.crm.common.excel.ExcelKit;
import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.mapper.CustomerMapper;
import com.aurum.crm.module.crm.mapper.OwnerChangeLogMapper;
import com.aurum.crm.module.crm.mapper.SysUserMiniMapper;
import com.aurum.crm.module.crm.model.CustomerDO;
import com.aurum.crm.module.crm.model.DictLabels;
import com.aurum.crm.module.crm.model.OwnerChangeLogDO;
import com.aurum.crm.module.crm.model.StarBizType;
import com.aurum.crm.module.crm.model.SysUserMiniDO;
import com.aurum.crm.module.crm.model.convert.CustomerConvert;
import com.aurum.crm.module.crm.model.dto.CustomerPageReqVO;
import com.aurum.crm.module.crm.model.dto.CustomerSaveReqVO;
import com.aurum.crm.module.crm.model.dto.CustomerSimplePageReqVO;
import com.aurum.crm.module.crm.model.dto.ToPoolReqVO;
import com.aurum.crm.module.crm.model.vo.CustomerExportRowVO;
import com.aurum.crm.module.crm.model.vo.CustomerRespVO;
import com.aurum.crm.module.crm.model.vo.CustomerSimpleRespVO;
import com.aurum.crm.module.crm.model.vo.SalesOwnerRespVO;
import com.aurum.crm.module.crm.service.CustomerService;
import com.aurum.crm.module.crm.service.BusinessNumberService;
import com.aurum.crm.module.crm.service.StarService;
import com.aurum.crm.module.system.service.OssStorageService;
import com.aurum.crm.mybatis.core.PageParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static com.aurum.crm.module.crm.service.impl.ExportSupport.timeStr;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客户 Service 实现：列表 / 详情 / 新增 / 修改 / 删除 / 放入公海。
 *
 * <p><b>数据权限不在这里写</b> —— 由 {@code crm-mybatis} 的
 * {@code DataPermissionInnerInterceptor} 统一改写 SQL，规则见《04-权限设计文档》第三节。
 *
 * <p><b>写操作的防越权靠两道</b>：
 * <ol>
 *     <li>{@link #update} / {@link #delete} / {@link #putToPool} 都先调 {@link #getCustomerDO}，
 *         而它走 {@code selectById} —— 拦截器<b>同样会改写主键查询</b>，
 *         越权时直接查不到、抛「客户不存在」，流程根本走不到写那一步</li>
 *     <li>即便漏了第一道，拦截器<b>对 UPDATE / DELETE 也生效</b>（MP 的
 *         {@code processUpdate}/{@code processDelete} 同样会调到 {@code buildTableExpression}），
 *         越权的写会因 WHERE 不匹配而影响 0 行</li>
 * </ol>
 * 所以这几个方法里不需要各写一遍权限校验。
 */
@Slf4j
@Service
public class CustomerServiceImpl implements CustomerService {

    /** 列表胶囊口径（{@code scope}），与前端 FILTERS 的 value 一一对应 */
    private static final String SCOPE_ALL = "all";
    private static final String SCOPE_MINE = "mine";
    private static final String SCOPE_SUBORDINATE = "subordinate";
    private static final String SCOPE_STARRED = "starred";
    private static final String SCOPE_DEALT = "dealt";
    private static final String SCOPE_UNDEALT = "undealt";

    /** 下属遍历的最大层级，防御脏数据造成的超长链（正常也就 3-4 层） */
    private static final int MAX_SUBORDINATE_DEPTH = 20;

    @Resource
    private CustomerMapper customerMapper;
    @Resource
    private OwnerChangeLogMapper ownerChangeLogMapper;
    @Resource
    private SysUserMiniMapper sysUserMiniMapper;
    @Resource
    private CustomerConvert customerConvert;
    @Resource
    private StarService starService;
    @Resource
    private OssStorageService ossStorageService;
    @Resource
    private BusinessNumberService businessNumberService;

    @Override
    public PageResult<CustomerRespVO> pageQuery(CustomerPageReqVO reqVO) {
        LambdaQueryWrapper<CustomerDO> wrapper = Wrappers.lambdaQuery();
        // 关键字：客户名称
        if (StringUtils.hasText(reqVO.getKeyword())) {
            // 关键字：客户名称 / 联系电话
            wrapper.and(w -> w.like(CustomerDO::getCustomerName, reqVO.getKeyword())
                    .or().like(CustomerDO::getPhone, reqVO.getKeyword()));
        }
        if (StringUtils.hasText(reqVO.getCustomerName())) {
            wrapper.like(CustomerDO::getCustomerName, reqVO.getCustomerName());
        }
        if (reqVO.getOwnerUserId() != null) {
            wrapper.eq(CustomerDO::getOwnerUserId, reqVO.getOwnerUserId());
        }
        if (reqVO.getIndustry() != null) {
            wrapper.eq(CustomerDO::getIndustry, reqVO.getIndustry());
        }
        if (reqVO.getSource() != null) {
            wrapper.eq(CustomerDO::getSource, reqVO.getSource());
        }
        if (reqVO.getLevel() != null) {
            wrapper.eq(CustomerDO::getLevel, reqVO.getLevel());
        }
        if (reqVO.getCreateTimeStart() != null) {
            wrapper.ge(CustomerDO::getCreateTime, reqVO.getCreateTimeStart());
        }
        if (reqVO.getCreateTimeEnd() != null) {
            wrapper.le(CustomerDO::getCreateTime, reqVO.getCreateTimeEnd());
        }
        if (reqVO.getDealStatus() != null) {
            wrapper.eq(CustomerDO::getDealStatus, reqVO.getDealStatus());
        }
        // 数据权限不在这里拼 —— 由 crm-mybatis 的 DataPermissionInnerInterceptor
        // 统一改写 SQL（见《04-权限设计文档》3.3）。早期这里手写过一份 applyDataScope，
        // 装上拦截器后必须删掉，否则两个条件会 AND 在一起变成双重过滤。
        // 胶囊口径（scope）—— 在数据权限之内再收窄，两者取交集
        if (!applyScope(wrapper, reqVO.getScope())) {
            return emptyResult(reqVO);
        }
        // 最近创建在前
        wrapper.orderByDesc(CustomerDO::getCreateTime);

        Page<CustomerDO> page = customerMapper.selectPage(reqVO, wrapper);
        return toPageResult(page);
    }

    @Override
    public CustomerRespVO get(Long id) {
        CustomerDO customerDO = getCustomerDO(id);
        return convertWithOwner(customerDO);
    }

    @Override
    public PageResult<CustomerSimpleRespVO> simpleList(CustomerSimplePageReqVO reqVO) {
        LambdaQueryWrapper<CustomerDO> wrapper = Wrappers.lambdaQuery();
        // 只取选择器真正要读的四列：省流量，也顺带避免把备注这类自由文本读出来
        wrapper.select(CustomerDO::getCustomerId,
                CustomerDO::getCustomerName,
                CustomerDO::getPhone,
                CustomerDO::getLevel,
                CustomerDO::getOwnerUserId);
        if (StringUtils.hasText(reqVO.getKeyword())) {
            wrapper.like(CustomerDO::getCustomerName, reqVO.getKeyword().trim());
        }
        // 选择器同样受数据权限约束（由拦截器改写 SQL）
        wrapper.orderByDesc(CustomerDO::getCreateTime).orderByDesc(CustomerDO::getCustomerId);

        Page<CustomerDO> page = customerMapper.selectPage(reqVO, wrapper);
        List<CustomerDO> records = page.getRecords();
        Map<Long, String> ownerNames = ownerNameMap(records);
        List<CustomerSimpleRespVO> list = records.stream().map(customer -> {
            CustomerSimpleRespVO vo = customerConvert.toSimpleRespVO(customer);
            vo.setOwnerName(ownerNames.get(customer.getOwnerUserId()));
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public List<SalesOwnerRespVO> ownerOptions() {
        return sysUserMiniMapper.selectSalesOwnerOptions();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CustomerSaveReqVO reqVO) {
        CustomerDO customerDO = customerConvert.toDO(reqVO);
        LoginUser loginUser = LoginUserHolder.getRequired();
        // 负责人缺省为当前登录人；显式指定时部门跟随该负责人
        if (customerDO.getOwnerUserId() == null) {
            customerDO.setOwnerUserId(loginUser.getUserId());
            customerDO.setDeptId(loginUser.getDeptId());
        } else {
            customerDO.setDeptId(ownerDeptId(customerDO.getOwnerUserId()));
        }
        customerDO.setCustomerNo(businessNumberService.next("CUSTOMER"));
        customerMapper.insert(customerDO);
        recordLog(customerDO, 1, customerDO.getCustomerName(),
                null, customerDO.getOwnerUserId(), null, "创建客户");
        return customerDO.getCustomerId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CustomerSaveReqVO reqVO) {
        CustomerDO existing = getCustomerDO(id);
        CustomerDO customerDO = customerConvert.toDO(reqVO);
        customerDO.setCustomerId(id);
        // 改派负责人：部门跟随新负责人，并记流转日志
        // （原在公海=分配，已有负责人=管理员改派；不传 ownerUserId 则不动负责人）
        Long newOwner = reqVO.getOwnerUserId();
        boolean ownerChanged = newOwner != null && !newOwner.equals(existing.getOwnerUserId());
        if (ownerChanged) {
            customerDO.setDeptId(ownerDeptId(newOwner));
        }
        customerMapper.updateById(customerDO);
        if (ownerChanged) {
            int action = existing.getOwnerUserId() == null ? 1 : 5;
            recordLog(existing, action, existing.getCustomerName(),
                    existing.getOwnerUserId(), newOwner, null, "修改客户时改派负责人");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getCustomerDO(id);
        customerMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void putToPool(Long id, ToPoolReqVO reqVO) {
        CustomerDO customerDO = getCustomerDO(id);
        Long fromOwner = customerDO.getOwnerUserId();
        if (fromOwner == null) {
            throw new ServiceException(ErrorCode.CUSTOMER_ALREADY_IN_POOL);
        }
        // ⚠️ updateById 默认忽略 null 字段，ownerUserId/deptId 置空根本写不进库，
        // 必须用 UpdateWrapper 显式 set null。eq(owner, fromOwner) 同时充当并发保护：
        // 负责人被并发改掉时影响 0 行，直接报冲突。
        int affected = customerMapper.update(null, Wrappers.<CustomerDO>lambdaUpdate()
                .eq(CustomerDO::getCustomerId, id)
                .eq(CustomerDO::getOwnerUserId, fromOwner)
                .set(CustomerDO::getOwnerUserId, null)
                .set(CustomerDO::getDeptId, null)
                .set(CustomerDO::getEnterPoolTime, LocalDateTime.now())
                .set(CustomerDO::getEnterPoolReasonCode, reqVO == null ? null : reqVO.getReasonCode())
                .set(CustomerDO::getEnterPoolRemark, reqVO == null ? null : reqVO.getReasonRemark())
                .set(CustomerDO::getPrevOwnerId, fromOwner));
        if (affected != 1) {
            throw new ServiceException(ErrorCode.RESOURCE_ALREADY_TAKEN);
        }
        recordLog(customerDO, 3, customerDO.getCustomerName(),
                fromOwner, null,
                reqVO == null ? null : reqVO.getReasonCode(),
                reqVO == null ? null : reqVO.getReasonRemark());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void star(Long id) {
        getCustomerDO(id); // 不存在的客户不能关注 → 2101
        starService.star(StarBizType.CUSTOMER, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unstar(Long id) {
        getCustomerDO(id); // 与 delete 一致，先做存在性校验
        starService.unstar(StarBizType.CUSTOMER, id);
    }

    @Override
    public String export(CustomerPageReqVO reqVO) {
        List<CustomerExportRowVO> rows = buildExportRows(reqVO);
        byte[] bytes = ExcelKit.write(rows, CustomerExportRowVO.class, ExportSupport.SHEET_CRM);
        return ExportSupport.upload(ossStorageService, bytes);
    }

    @Override
    public String exportXml(CustomerPageReqVO reqVO) {
        List<CustomerExportRowVO> rows = buildExportRows(reqVO);
        return ExportSupport.toXmlUrl(ossStorageService, rows, CustomerExportRowVO.class);
    }

    /** 导出用：复用列表过滤条件查数，并映射为导出行（不截断到单页，数据权限由拦截器兜底） */
    private List<CustomerExportRowVO> buildExportRows(CustomerPageReqVO reqVO) {
        List<CustomerDO> records = listForExport(reqVO);
        Map<Long, String> ownerNameMap = ownerNameMap(records);

        return records.stream().map(c -> {
            CustomerExportRowVO row = new CustomerExportRowVO();
            row.setCustomerNo(c.getCustomerNo());
            row.setCustomerName(c.getCustomerName());
            row.setIndustry(DictLabels.industry(c.getIndustry()));
            row.setLevel(DictLabels.level(c.getLevel()));
            row.setDealStatus(DictLabels.dealStatus(c.getDealStatus()));
            row.setSource(DictLabels.source(c.getSource()));
            row.setPhone(c.getPhone());
            row.setProvince(c.getProvince());
            row.setCity(c.getCity());
            row.setDistrict(c.getDistrict());
            row.setAddress(c.getAddress());
            row.setOwnerName(ownerNameMap.get(c.getOwnerUserId()));
            row.setCreateTime(timeStr(c.getCreateTime()));
            return row;
        }).collect(Collectors.toList());
    }

    // ==================== 内部方法 ====================

    /** 分页结果转 VO 并补负责人姓名 / 关注态 */
    private PageResult<CustomerRespVO> toPageResult(Page<CustomerDO> page) {
        List<CustomerDO> records = page.getRecords();
        Map<Long, String> ownerNameMap = ownerNameMap(records);
        // 批量查关注态，避免逐行查库；空页时 starredBizIds 返回空集
        List<Long> customerIds = records.stream()
                .map(CustomerDO::getCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Set<Long> starredIds = starService.starredBizIds(StarBizType.CUSTOMER, customerIds);
        List<CustomerRespVO> list = records.stream()
                .map(c -> {
                    CustomerRespVO vo = customerConvert.toRespVO(c);
                    vo.setOwnerName(ownerNameMap.get(c.getOwnerUserId()));
                    vo.setStarred(starredIds.contains(c.getCustomerId()));
                    return vo;
                })
                .collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    private CustomerRespVO convertWithOwner(CustomerDO customerDO) {
        CustomerRespVO vo = customerConvert.toRespVO(customerDO);
        if (customerDO.getOwnerUserId() != null) {
            SysUserMiniDO user = sysUserMiniMapper.selectById(customerDO.getOwnerUserId());
            if (user != null) {
                vo.setOwnerName(user.getRealName());
            }
        }
        vo.setStarred(starService.isStarred(StarBizType.CUSTOMER, customerDO.getCustomerId()));
        return vo;
    }

    /**
     * 按 ID 取客户，取不到抛「客户不存在」。
     *
     * <p>⚠️ 这里的 {@code selectById} <b>会被数据权限拦截器改写</b>，
     * 所以「越权访问别人的客户」和「客户真的不存在」返回的是同一个结果 ——
     * 这是<b>有意为之</b>，不泄露「这个 ID 存在但你看不到」，与前端页面级跳 404 的取舍一致。
     * 不要把它改成「先查再判断是否有权限」，那会把这个信息泄露出去。
     *
     * <p>正因为写操作都先经过这里，{@code update} / {@code delete} / {@code putToPool}
     * 才自动获得了越权保护。
     */
    private CustomerDO getCustomerDO(Long id) {
        CustomerDO customerDO = customerMapper.selectById(id);
        if (customerDO == null) {
            throw new ServiceException(ErrorCode.CUSTOMER_NOT_EXISTS);
        }
        return customerDO;
    }

    /**
     * 查目标负责人并返回其部门ID，目标用户不存在时抛 2105。
     *
     * <p>创建/改派时「部门跟随负责人」，不能拍脑袋取操作人的部门。
     */
    private Long ownerDeptId(Long ownerUserId) {
        SysUserMiniDO owner = sysUserMiniMapper.selectById(ownerUserId);
        if (owner == null) {
            throw new ServiceException(ErrorCode.CUSTOMER_TARGET_USER_NOT_EXISTS);
        }
        return owner.getDeptId();
    }

    /**
     * 胶囊口径过滤（{@code scope}），与数据权限拦截器是<b>交集</b>关系 ——
     * 用户主动选的取数口径只能在数据权限允许的范围内继续收窄。
     *
     * <p>只追加裸的 {@code eq} / {@code in} 连接词，绝不追加裸 {@code or}；
     * 需要 OR 的场景都已包在各自的 {@code Consumer} 里。
     *
     * @return {@code false} 表示结果必然为空，调用方应直接返回空页 ——
     * 这同时是「不给 MyBatis-Plus 传空集合」的守卫。
     */
    private boolean applyScope(LambdaQueryWrapper<CustomerDO> wrapper, String scope) {
        if (!StringUtils.hasText(scope) || SCOPE_ALL.equals(scope)) {
            return true;
        }
        LoginUser me = LoginUserHolder.getRequired();
        switch (scope) {
            case SCOPE_MINE -> {
                if (me.getUserId() == null) {
                    return false;
                }
                wrapper.eq(CustomerDO::getOwnerUserId, me.getUserId());
            }
            case SCOPE_SUBORDINATE -> {
                List<Long> subordinateIds = listSubordinateUserIds(me.getUserId());
                if (subordinateIds.isEmpty()) {
                    return false;
                }
                wrapper.in(CustomerDO::getOwnerUserId, subordinateIds);
            }
            case SCOPE_STARRED -> {
                Set<Long> starredIds = starService.starredBizIds(StarBizType.CUSTOMER);
                if (starredIds.isEmpty()) {
                    return false;
                }
                wrapper.in(CustomerDO::getCustomerId, starredIds);
            }
            case SCOPE_DEALT -> wrapper.eq(CustomerDO::getDealStatus, 2);
            case SCOPE_UNDEALT -> wrapper.eq(CustomerDO::getDealStatus, 1);
            // 未登记的取值不静默放行，直接 400 更容易发现前端漏配
            default -> throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "不支持的 scope 取值：" + scope);
        }
        return true;
    }

    /**
     * 沿 {@code sys_user.superior_id} 自顶向下逐层收集「我」的全部下属（多层，<b>不含自己</b>）。
     * 与 {@link LeadServiceImpl#listSubordinateUserIds} 同构。
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

    /** 分页空结果，照搬 lead 的同名方法 */
    private <T> PageResult<T> emptyResult(PageParam reqVO) {
        Page<?> page = reqVO.toPage();
        return PageResult.empty((int) page.getCurrent(), (int) page.getSize());
    }

    /** 导出用：复用列表过滤条件，取全部记录（不翻页），数据权限由拦截器兜底 */
    private List<CustomerDO> listForExport(CustomerPageReqVO reqVO) {
        LambdaQueryWrapper<CustomerDO> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(reqVO.getKeyword())) {
            String keyword = reqVO.getKeyword();
            wrapper.and(w -> w.like(CustomerDO::getCustomerName, keyword)
                    .or().like(CustomerDO::getPhone, keyword));
        }
        if (StringUtils.hasText(reqVO.getCustomerName())) {
            wrapper.like(CustomerDO::getCustomerName, reqVO.getCustomerName());
        }
        if (reqVO.getOwnerUserId() != null) {
            wrapper.eq(CustomerDO::getOwnerUserId, reqVO.getOwnerUserId());
        }
        if (reqVO.getIndustry() != null) {
            wrapper.eq(CustomerDO::getIndustry, reqVO.getIndustry());
        }
        if (reqVO.getSource() != null) {
            wrapper.eq(CustomerDO::getSource, reqVO.getSource());
        }
        if (reqVO.getLevel() != null) {
            wrapper.eq(CustomerDO::getLevel, reqVO.getLevel());
        }
        if (reqVO.getDealStatus() != null) {
            wrapper.eq(CustomerDO::getDealStatus, reqVO.getDealStatus());
        }
        if (!applyScope(wrapper, reqVO.getScope())) {
            return List.of();
        }
        wrapper.orderByDesc(CustomerDO::getCreateTime);
        return customerMapper.selectList(wrapper);
    }

    /** 批量查负责人姓名 */
    private Map<Long, String> ownerNameMap(List<CustomerDO> records) {
        List<Long> ownerIds = records.stream()
                .map(CustomerDO::getOwnerUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ownerIds.isEmpty()) {
            // ⚠️ 必须用 Collections.emptyMap() 而不是 Map.of()：Map.of() 是不可变 Map，
            //    空实例的 get() 会先 requireNonNull(key)，而调用处会拿 owner_user_id 为 null
            //    的行（公海客户）来 get —— 于是「整页都是公海客户」时列表直接 500。
            //    线索 / 产品 / 商机 / 报价单 / 合同 / 看板 / 线索池七处都踩过这个坑，
            //    见 LeadServiceImpl#ownerNameMap 等处的同名注释。
            return Collections.emptyMap();
        }
        return sysUserMiniMapper.selectList(SysUserMiniDO::getUserId, ownerIds).stream()
                .collect(Collectors.toMap(SysUserMiniDO::getUserId, SysUserMiniDO::getRealName,
                        (a, b) -> a));
    }

    /** 写入归属流转记录 */
    private void recordLog(CustomerDO customer, Integer action, String bizName,
                           Long fromOwner, Long toOwner, Integer reasonCode, String reasonRemark) {
        OwnerChangeLogDO logDO = new OwnerChangeLogDO();
        logDO.setBizType(2);
        logDO.setBizId(customer.getCustomerId());
        logDO.setBizName(bizName);
        logDO.setAction(action);
        logDO.setFromOwnerId(fromOwner);
        logDO.setToOwnerId(toOwner);
        logDO.setReasonCode(reasonCode);
        logDO.setReasonRemark(reasonRemark);
        logDO.setOperatorId(LoginUserHolder.getUserId());
        ownerChangeLogMapper.insert(logDO);
    }
}
