package com.aurum.crm.module.crm.service.impl;

import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.mapper.LeadMapper;
import com.aurum.crm.module.crm.mapper.OwnerChangeLogMapper;
import com.aurum.crm.module.crm.mapper.SysUserMiniMapper;
import com.aurum.crm.module.crm.model.LeadDO;
import com.aurum.crm.module.crm.model.DictLabels;
import com.aurum.crm.module.crm.model.OwnerChangeLogDO;
import com.aurum.crm.module.crm.model.StarBizType;
import com.aurum.crm.module.crm.model.SysUserMiniDO;
import com.aurum.crm.module.crm.model.convert.LeadConvert;
import com.aurum.crm.module.crm.model.dto.CustomerSaveReqVO;
import com.aurum.crm.module.crm.model.dto.LeadPageReqDTO;
import com.aurum.crm.module.crm.model.dto.LeadSaveReqDTO;
import com.aurum.crm.module.crm.model.dto.LeadSimplePageReqDTO;
import com.aurum.crm.module.crm.model.dto.ToLeadPoolReqDTO;
import com.aurum.crm.module.crm.model.vo.LeadExportRowVO;
import com.aurum.crm.module.crm.model.vo.LeadRespVO;
import com.aurum.crm.module.crm.model.vo.LeadSimpleRespVO;
import com.aurum.crm.module.crm.service.LeadService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 线索 Service 实现：列表 / 详情 / 新增 / 修改 / 删除 / 放入线索池。
 *
 * <p>列表查询自动注入数据权限（本人+线索池 / 本部门及以下+线索池 / 全部），
 * 与《04-权限设计文档》第三节一致。
 */
@Slf4j
@Service
public class LeadServiceImpl implements LeadService {

    /** 列表胶囊口径（{@code scope}），与前端 FILTERS 的 value 一一对应 */
    private static final String SCOPE_ALL = "all";
    private static final String SCOPE_MINE = "mine";
    private static final String SCOPE_SUBORDINATE = "subordinate";
    private static final String SCOPE_STARRED = "starred";

    /** 下属遍历的最大层级，防御脏数据造成的超长链（正常也就 3-4 层） */
    private static final int MAX_SUBORDINATE_DEPTH = 20;

    @Resource
    private LeadMapper leadMapper;
    @Resource
    private OwnerChangeLogMapper ownerChangeLogMapper;
    @Resource
    private SysUserMiniMapper sysUserMiniMapper;
    @Resource
    private LeadConvert leadConvert;
    @Resource
    private StarService starService;
    @Resource
    private OssStorageService ossStorageService;
    @Resource
    private BusinessNumberService businessNumberService;
    @Resource
    private CustomerService customerService;

    @Override
    public PageResult<LeadRespVO> pageQuery(LeadPageReqDTO reqDTO) {
        LambdaQueryWrapper<LeadDO> wrapper = Wrappers.lambdaQuery();
        // 关键字：线索名称 / 联系人姓名 / 联系电话
        if (StringUtils.hasText(reqDTO.getKeyword())) {
            String keyword = reqDTO.getKeyword();
            wrapper.and(w -> w.like(LeadDO::getLeadName, keyword)
                    .or().like(LeadDO::getContactName, keyword)
                    .or().like(LeadDO::getPhone, keyword));
        }
        if (StringUtils.hasText(reqDTO.getLeadName())) {
            wrapper.like(LeadDO::getLeadName, reqDTO.getLeadName());
        }
        if (reqDTO.getOwnerUserId() != null) {
            wrapper.eq(LeadDO::getOwnerUserId, reqDTO.getOwnerUserId());
        }
        if (reqDTO.getSource() != null) {
            wrapper.eq(LeadDO::getSource, reqDTO.getSource());
        }
        if (reqDTO.getIndustry() != null) {
            wrapper.eq(LeadDO::getIndustry, reqDTO.getIndustry());
        }
        if (reqDTO.getCreateTimeStart() != null) {
            wrapper.ge(LeadDO::getCreateTime, reqDTO.getCreateTimeStart());
        }
        if (reqDTO.getCreateTimeEnd() != null) {
            wrapper.le(LeadDO::getCreateTime, reqDTO.getCreateTimeEnd());
        }
        // 数据权限
        applyDataScope(wrapper);
        // 胶囊口径（scope）—— 在数据权限之内再收窄，两者取交集
        if (!applyScope(wrapper, reqDTO.getScope())) {
            // 结果必然为空（无下属 / 没关注过任何线索）：不查库直接空页。
            // 顺带避开 MyBatis-Plus 对空集合生成非法 IN () 的坑，见 applyScope 注释。
            return emptyResult(reqDTO);
        }
        // 最近创建在前
        wrapper.orderByDesc(LeadDO::getCreateTime);

        Page<LeadDO> page = leadMapper.selectPage(reqDTO, wrapper);
        return toPageResult(page);
    }

    @Override
    public LeadRespVO get(Long id) {
        LeadDO leadDO = getLeadDO(id);
        return convertWithOwner(leadDO);
    }

    @Override
    public PageResult<LeadSimpleRespVO> simpleList(LeadSimplePageReqDTO reqDTO) {
        LambdaQueryWrapper<LeadDO> wrapper = Wrappers.lambdaQuery();
        // 只取选择器真正要读的三列：省流量，也顺带避免把备注这类自由文本读出来
        wrapper.select(LeadDO::getLeadId,
                LeadDO::getLeadName,
                LeadDO::getPhone,
                LeadDO::getOwnerUserId);
        if (StringUtils.hasText(reqDTO.getKeyword())) {
            wrapper.like(LeadDO::getLeadName, reqDTO.getKeyword().trim());
        }
        // 选择器同样受数据权限约束（由拦截器改写 SQL）
        wrapper.orderByDesc(LeadDO::getCreateTime).orderByDesc(LeadDO::getLeadId);

        Page<LeadDO> page = leadMapper.selectPage(reqDTO, wrapper);
        List<LeadDO> records = page.getRecords();
        Map<Long, String> ownerNames = ownerNameMap(records);
        List<LeadSimpleRespVO> list = records.stream().map(lead -> {
            LeadSimpleRespVO vo = leadConvert.toSimpleRespVO(lead);
            vo.setOwnerName(ownerNames.get(lead.getOwnerUserId()));
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(LeadSaveReqDTO reqDTO) {
        LeadDO leadDO = leadConvert.toDO(reqDTO);
        leadDO.setNextContactTime(parseDateLeniently(reqDTO.getNextContactTime()));
        LoginUser loginUser = LoginUserHolder.getRequired();
        // 负责人缺省为当前登录人；编号统一由编号规则引擎生成
        if (leadDO.getOwnerUserId() == null) {
            leadDO.setOwnerUserId(loginUser.getUserId());
        }
        if (leadDO.getDeptId() == null) {
            leadDO.setDeptId(loginUser.getDeptId());
        }
        leadDO.setLeadNo(businessNumberService.next("LEAD"));
        leadMapper.insert(leadDO);
        recordLog(leadDO, 1, leadDO.getLeadName(),
                null, leadDO.getOwnerUserId(), null, "创建线索");
        return leadDO.getLeadId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, LeadSaveReqDTO reqDTO) {
        getLeadDO(id);
        LeadDO leadDO = leadConvert.toDO(reqDTO);
        leadDO.setLeadId(id);
        leadDO.setNextContactTime(parseDateLeniently(reqDTO.getNextContactTime()));
        leadMapper.updateById(leadDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getLeadDO(id);
        leadMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            // 空集合直接返回：既无事可做，也避开 MyBatis-Plus 生成非法 IN () 的坑
            return;
        }
        // 不做存在性校验 —— 批量里某条已被别人删掉是常态，为它整批失败会逼用户反复重试。
        // 走 @TableLogic，实际是 UPDATE crm_lead SET del_flag = 1 WHERE lead_id IN (...)
        leadMapper.delete(Wrappers.<LeadDO>lambdaQuery().in(LeadDO::getLeadId, ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void putToPool(Long id, ToLeadPoolReqDTO reqDTO) {
        LeadDO leadDO = getLeadDO(id);
        Long fromOwner = leadDO.getOwnerUserId();
        if (fromOwner == null) {
            // 已经在池里了（线索池 = owner_user_id IS NULL），再放一次没有意义。
            // 同公海的 CustomerServiceImpl#putToPool，用专门的错误码给出准确提示。
            throw new ServiceException(ErrorCode.LEAD_ALREADY_IN_POOL);
        }
        // ⚠️ updateById 默认忽略 null 字段（见 application.yml 的 update-strategy: not_null），
        //    ownerUserId / deptId 置空根本写不进库 —— 接口照样返回成功，
        //    但线索还在原负责人名下，线索池（owner_user_id IS NULL）里永远查不到它。
        //    必须用 UpdateWrapper 显式 set null，同公海的 CustomerServiceImpl#putToPool。
        //    eq(ownerUserId, fromOwner) 同时充当并发保护：负责人被并发改掉时影响 0 行。
        int affected = leadMapper.update(null, Wrappers.<LeadDO>lambdaUpdate()
                .eq(LeadDO::getLeadId, id)
                .eq(LeadDO::getOwnerUserId, fromOwner)
                .set(LeadDO::getOwnerUserId, null)
                .set(LeadDO::getDeptId, null)
                .set(LeadDO::getEnterPoolTime, LocalDateTime.now())
                .set(LeadDO::getEnterPoolReasonCode, reqDTO == null ? null : reqDTO.getReasonCode())
                .set(LeadDO::getEnterPoolRemark, reqDTO == null ? null : reqDTO.getReasonRemark())
                .set(LeadDO::getPrevOwnerId, fromOwner));
        if (affected != 1) {
            throw new ServiceException(ErrorCode.RESOURCE_ALREADY_TAKEN);
        }
        recordLog(leadDO, 3, leadDO.getLeadName(),
                fromOwner, null,
                reqDTO == null ? null : reqDTO.getReasonCode(),
                reqDTO == null ? null : reqDTO.getReasonRemark());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void star(Long id) {
        getLeadDO(id); // 不存在的线索不能关注 → 2001
        starService.star(StarBizType.LEAD, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unstar(Long id) {
        getLeadDO(id); // 与 delete 一致，先做存在性校验
        starService.unstar(StarBizType.LEAD, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long convert(Long id) {
        LeadDO lead = getLeadDO(id);
        if (lead.getConvertedCustomerId() != null) {
            throw new ServiceException(ErrorCode.LEAD_ALREADY_CONVERTED);
        }

        // 客户字段对齐 lead 的同名字段；customerName 用线索的公司/客户名，leadId 建立溯源。
        // 其余（编号 / 负责人缺省当前登录人 / 部门跟随负责人 / 客户侧流水）交给 CustomerService.create。
        CustomerSaveReqVO reqVO = new CustomerSaveReqVO();
        reqVO.setCustomerName(lead.getLeadName());
        reqVO.setLeadId(lead.getLeadId());
        reqVO.setIndustry(lead.getIndustry());
        reqVO.setLevel(lead.getLevel());
        reqVO.setSource(lead.getSource());
        reqVO.setPhone(lead.getPhone());
        reqVO.setEmail(lead.getEmail());
        reqVO.setProvince(lead.getProvince());
        reqVO.setCity(lead.getCity());
        reqVO.setDistrict(lead.getDistrict());
        reqVO.setAddress(lead.getAddress());
        reqVO.setOwnerUserId(lead.getOwnerUserId());
        reqVO.setRemark(lead.getRemark());
        // 转化后默认未成交，由后续商机/合同阶段驱动成交状态
        Long customerId = customerService.create(reqVO);

        // 在线索上钉下「已转化」标记。leads 侧用 updateById 即可（所有字段都有值），
        // convertedCustomerId / convertTime 是本次唯一要写的两列。
        lead.setConvertedCustomerId(customerId);
        lead.setConvertTime(LocalDateTime.now());
        leadMapper.updateById(lead);

        // 线索侧的「线索转化」流转记录（action=6），从原负责人转给原负责人（归属不变），
        // 只是留一条可追踪的水流账，供线索详情的时间线展示。
        recordLog(lead, 6, lead.getLeadName(),
                lead.getOwnerUserId(), lead.getOwnerUserId(), null,
                "已转为客户「" + lead.getLeadName() + "」");
        return customerId;
    }

    @Override
    public String export(LeadPageReqDTO reqDTO) {
        List<LeadExportRowVO> rows = buildExportRows(reqDTO);
        return ExportSupport.toUrl(ossStorageService, rows, LeadExportRowVO.class);
    }

    @Override
    public String exportXml(LeadPageReqDTO reqDTO) {
        List<LeadExportRowVO> rows = buildExportRows(reqDTO);
        return ExportSupport.toXmlUrl(ossStorageService, rows, LeadExportRowVO.class);
    }

    /** 导出用：复用列表查询条件查数并映射为导出行 */
    private List<LeadExportRowVO> buildExportRows(LeadPageReqDTO reqDTO) {
        // 导出复用列表查询条件（含数据权限 + scope），取全部结果
        List<LeadDO> records = listForExport(reqDTO);
        Map<Long, String> ownerNames = ownerNameMap(records);

        return records.stream().map(l -> {
            LeadExportRowVO row = new LeadExportRowVO();
            row.setLeadNo(l.getLeadNo());
            row.setLeadName(l.getLeadName());
            row.setContactName(l.getContactName());
            row.setPosition(l.getPosition());
            row.setPhone(l.getPhone());
            row.setIndustry(DictLabels.industry(l.getIndustry()));
            row.setSource(DictLabels.source(l.getSource()));
            row.setProvince(l.getProvince());
            row.setCity(l.getCity());
            row.setDistrict(l.getDistrict());
            row.setAddress(l.getAddress());
            row.setOwnerName(ownerNames.get(l.getOwnerUserId()));
            row.setEnterPoolTime(ExportSupport.timeStr(l.getEnterPoolTime()));
            row.setEnterPoolReason(DictLabels.poolReason(l.getEnterPoolReasonCode()));
            row.setLastFollowTime(ExportSupport.timeStr(l.getLastFollowTime()));
            row.setCreateTime(ExportSupport.timeStr(l.getCreateTime()));
            return row;
        }).collect(Collectors.toList());
    }

    /** 导出用：复用列表过滤条件，取全部记录（不翻页），数据权限 + scope 已含 */
    private List<LeadDO> listForExport(LeadPageReqDTO reqDTO) {
        LambdaQueryWrapper<LeadDO> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(reqDTO.getKeyword())) {
            String keyword = reqDTO.getKeyword();
            wrapper.and(w -> w.like(LeadDO::getLeadName, keyword)
                    .or().like(LeadDO::getContactName, keyword)
                    .or().like(LeadDO::getPhone, keyword));
        }
        if (StringUtils.hasText(reqDTO.getLeadName())) {
            wrapper.like(LeadDO::getLeadName, reqDTO.getLeadName());
        }
        if (reqDTO.getOwnerUserId() != null) {
            wrapper.eq(LeadDO::getOwnerUserId, reqDTO.getOwnerUserId());
        }
        if (reqDTO.getSource() != null) {
            wrapper.eq(LeadDO::getSource, reqDTO.getSource());
        }
        if (reqDTO.getIndustry() != null) {
            wrapper.eq(LeadDO::getIndustry, reqDTO.getIndustry());
        }
        if (reqDTO.getCreateTimeStart() != null) {
            wrapper.ge(LeadDO::getCreateTime, reqDTO.getCreateTimeStart());
        }
        if (reqDTO.getCreateTimeEnd() != null) {
            wrapper.le(LeadDO::getCreateTime, reqDTO.getCreateTimeEnd());
        }
        applyDataScope(wrapper);
        if (!applyScope(wrapper, reqDTO.getScope())) {
            return List.of();
        }
        wrapper.orderByDesc(LeadDO::getCreateTime);
        return leadMapper.selectList(wrapper);
    }

    // ==================== 内部方法 ====================

    /** 数据权限过滤：仅本人 / 本部门及以下 / 全部 */
    private void applyDataScope(LambdaQueryWrapper<LeadDO> wrapper) {
        LoginUser loginUser = LoginUserHolder.getRequired();
        Integer dataScope = loginUser.getDataScope();
        // dataScope：1 全部 / 3 本部门 / 4 本部门及以下 / 5 仅本人；null 或 2 自定义保守取本人
        if (dataScope != null && dataScope == 1) {
            return; // 全部
        }
        if (dataScope != null && (dataScope == 3 || dataScope == 4)) {
            // 本部门及以下 / 本部门：按归属人所在部门过滤
            Long deptId = loginUser.getDeptId();
            if (deptId != null) {
                wrapper.and(w -> w.eq(LeadDO::getDeptId, deptId));
            }
            return;
        }
        // 默认：仅本人（含线索池）
        Long userId = loginUser.getUserId();
        if (userId != null) {
            wrapper.and(w -> w.eq(LeadDO::getOwnerUserId, userId)
                    .or().isNull(LeadDO::getOwnerUserId));
        }
    }

    /**
     * 胶囊口径过滤（{@code scope}），与 {@link #applyDataScope} 是<b>交集</b>关系 ——
     * 用户主动选的取数口径只能在数据权限允许的范围内继续收窄，
     * 不能让没有权限的人靠一个请求参数看到别人的数据。
     *
     * <p>只追加裸的 {@code eq} / {@code in} 连接词，绝不追加裸 {@code or}，
     * 否则会漏到同层其他条件上；需要 OR 的场景都已包在各自的 {@code Consumer} 里。
     *
     * @return {@code false} 表示结果必然为空，调用方应直接返回空页 ——
     * 这同时是「不给 MyBatis-Plus 传空集合」的守卫：3.5.7 的
     * {@code in(col, empty)} 会生成非法的 {@code IN ()}（本仓库其它 Service
     * 在每次 {@code in} 前都做了 isEmpty 判断，同一原因）。
     */
    private boolean applyScope(LambdaQueryWrapper<LeadDO> wrapper, String scope) {
        if (!StringUtils.hasText(scope) || SCOPE_ALL.equals(scope)) {
            return true; // 全部：口径不额外收窄
        }
        LoginUser me = LoginUserHolder.getRequired();
        switch (scope) {
            case SCOPE_MINE -> {
                // 我负责的 = owner 是我本人（不含线索池）
                if (me.getUserId() == null) {
                    return false;
                }
                wrapper.eq(LeadDO::getOwnerUserId, me.getUserId());
            }
            case SCOPE_SUBORDINATE -> {
                // 下属负责的 = owner 落在「我的全部下属」集合里（不含我）
                List<Long> subordinateIds = listSubordinateUserIds(me.getUserId());
                if (subordinateIds.isEmpty()) {
                    return false;
                }
                wrapper.in(LeadDO::getOwnerUserId, subordinateIds);
            }
            case SCOPE_STARRED -> {
                // 我关注的 = 我关注过的线索（跨归属，所以按 lead_id 而不是 owner 过滤）
                Set<Long> starredLeadIds = starService.starredBizIds(StarBizType.LEAD);
                if (starredLeadIds.isEmpty()) {
                    return false;
                }
                wrapper.in(LeadDO::getLeadId, starredLeadIds);
            }
            // 未登记的取值不静默放行：前端加了胶囊而后端漏配时，
            // 静默返回未过滤的数据会让人以为功能正常，直接 400 更容易发现
            default -> throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "不支持的 scope 取值：" + scope);
        }
        return true;
    }

    /**
     * 沿 {@code sys_user.superior_id} 自顶向下逐层收集「我」的全部下属（多层，<b>不含自己</b>）。
     *
     * <p>{@code visited} 先放入 {@code rootUserId} 再遍历，一举两得：
     * 既是环守卫（A→B→A 时剪断），又保证每人只入队一次。
     * 循环条件保证 {@code frontier} 入参永不为空 —— 空集合会生成非法的
     * {@code IN ()}（见 {@link #applyScope}）。
     */
    private List<Long> listSubordinateUserIds(Long rootUserId) {
        if (rootUserId == null) {
            return List.of();
        }
        Set<Long> visited = new HashSet<>();
        visited.add(rootUserId); // 只占位，不进结果 → 下属不含自己
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
        if (!frontier.isEmpty()) {
            log.warn("[线索列表] 下属层级超过 {} 层，已截断。rootUserId={}",
                    MAX_SUBORDINATE_DEPTH, rootUserId);
        }
        return result;
    }

    /**
     * 分页空结果。照搬 {@code ProductServiceImpl} 的同名方法：
     * 「筛选条件失效不该中断查询」，返回空页而不是报错。
     */
    private <T> PageResult<T> emptyResult(PageParam reqVO) {
        Page<?> page = reqVO.toPage();
        return PageResult.empty((int) page.getCurrent(), (int) page.getSize());
    }

    /** 分页结果转 VO 并补负责人姓名 / 创建人姓名 / 关注态 */
    private PageResult<LeadRespVO> toPageResult(Page<LeadDO> page) {
        List<LeadDO> records = page.getRecords();
        Map<Long, String> ownerNameMap = ownerNameMap(records);
        Map<Long, String> creatorNameMap = userNameMap(records.stream()
                .map(LeadDO::getCreateBy).toList());
        // 批量查关注态，避免逐行查库；空页时 starredBizIds 返回空集
        List<Long> leadIds = records.stream()
                .map(LeadDO::getLeadId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Set<Long> starredIds = starService.starredBizIds(StarBizType.LEAD, leadIds);
        List<LeadRespVO> list = records.stream()
                .map(l -> {
                    LeadRespVO vo = leadConvert.toRespVO(l);
                    vo.setOwnerName(ownerNameMap.get(l.getOwnerUserId()));
                    vo.setCreator(creatorNameMap.get(l.getCreateBy()));
                    vo.setStarred(starredIds.contains(l.getLeadId()));
                    return vo;
                })
                .collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    private LeadRespVO convertWithOwner(LeadDO leadDO) {
        LeadRespVO vo = leadConvert.toRespVO(leadDO);
        if (leadDO.getOwnerUserId() != null) {
            SysUserMiniDO user = sysUserMiniMapper.selectById(leadDO.getOwnerUserId());
            if (user != null) {
                vo.setOwnerName(user.getRealName());
            }
        }
        vo.setCreator(loadUserName(leadDO.getCreateBy()));
        vo.setStarred(starService.isStarred(StarBizType.LEAD, leadDO.getLeadId()));
        return vo;
    }

    /**
     * 宽容解析日期。
     *
     * <p>为什么请求侧收字符串而不是直接收 {@code LocalDate}：见
     * {@link LeadSaveReqDTO#getNextContactTime()} —— 前端从没选过日期时提交的是
     * <b>空字符串</b>，而 Jackson 会把 {@code ""} 交给 {@code LocalDate.parse} 抛异常。
     */
    private LocalDate parseDateLeniently(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "下次联系时间格式不正确，应为 yyyy-MM-dd，实际：" + text);
        }
    }

    /** 批量查用户姓名（按 id 集合） */
    private Map<Long, String> userNameMap(Collection<Long> userIds) {
        List<Long> distinctIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            // ⚠️ 这里必须是 Collections.emptyMap() 而不是 Map.of()：
            //    后者是 ImmutableCollections.MapN，size == 0 时 get() 会先
            //    Objects.requireNonNull(key)，而下面调用处传的正是 null（线索在池中时
            //    ownerUserId 为 null），会抛 NPE 让整个列表接口 500。
            //    商机模块的 userNameMap 已经用了 emptyMap，线索这里曾经是 Map.of()。
            return Collections.emptyMap();
        }
        return sysUserMiniMapper.selectList(SysUserMiniDO::getUserId, distinctIds).stream()
                .filter(user -> user.getRealName() != null)
                .collect(Collectors.toMap(SysUserMiniDO::getUserId, SysUserMiniDO::getRealName,
                        (a, b) -> a));
    }

    /** 查单个用户姓名 */
    private String loadUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUserMiniDO user = sysUserMiniMapper.selectById(userId);
        return user == null ? null : user.getRealName();
    }

    private LeadDO getLeadDO(Long id) {
        LeadDO leadDO = leadMapper.selectById(id);
        if (leadDO == null) {
            throw new ServiceException(ErrorCode.LEAD_NOT_EXISTS);
        }
        return leadDO;
    }

    /** 批量查负责人姓名 */
    private Map<Long, String> ownerNameMap(List<LeadDO> records) {
        List<Long> ownerIds = records.stream()
                .map(LeadDO::getOwnerUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ownerIds.isEmpty()) {
            // 同 userNameMap：不能用 Map.of() —— 调用处会 get(null)
            return Collections.emptyMap();
        }
        return sysUserMiniMapper.selectList(SysUserMiniDO::getUserId, ownerIds).stream()
                .collect(Collectors.toMap(SysUserMiniDO::getUserId, SysUserMiniDO::getRealName,
                        (a, b) -> a));
    }

    /** 写入归属流转记录 */
    private void recordLog(LeadDO lead, Integer action, String bizName,
                           Long fromOwner, Long toOwner, Integer reasonCode, String reasonRemark) {
        OwnerChangeLogDO logDO = new OwnerChangeLogDO();
        logDO.setBizType(1);
        logDO.setBizId(lead.getLeadId());
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
