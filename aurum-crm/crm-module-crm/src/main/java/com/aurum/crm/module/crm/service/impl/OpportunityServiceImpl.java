package com.aurum.crm.module.crm.service.impl;

import com.aurum.crm.common.core.DataScope;
import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.mapper.CustomerMiniMapper;
import com.aurum.crm.module.crm.mapper.OpportunityItemMapper;
import com.aurum.crm.module.crm.mapper.OpportunityMapper;
import com.aurum.crm.module.crm.mapper.OpportunityStageLogMapper;
import com.aurum.crm.module.crm.mapper.SysUserMiniMapper;
import com.aurum.crm.module.crm.model.CustomerMiniDO;
import com.aurum.crm.module.crm.model.DictLabels;
import com.aurum.crm.module.crm.model.OpportunityDO;
import com.aurum.crm.module.crm.model.OpportunityItemDO;
import com.aurum.crm.module.crm.model.OpportunityStage;
import com.aurum.crm.module.crm.model.OpportunityStageLogDO;
import com.aurum.crm.module.crm.model.SysUserMiniDO;
import com.aurum.crm.module.crm.model.convert.OpportunityConvert;
import com.aurum.crm.module.crm.model.dto.DocumentItemSaveReqVO;
import com.aurum.crm.module.crm.model.dto.OpportunityPageReqVO;
import com.aurum.crm.module.crm.model.dto.OpportunitySaveReqVO;
import com.aurum.crm.module.crm.model.dto.OpportunitySimplePageReqVO;
import com.aurum.crm.module.crm.model.dto.OpportunityStageChangeReqVO;
import com.aurum.crm.module.crm.model.vo.DocumentItemRespVO;
import com.aurum.crm.module.crm.model.vo.OpportunityExportRowVO;
import com.aurum.crm.module.crm.model.vo.OpportunityFunnelRespVO;
import com.aurum.crm.module.crm.model.vo.OpportunityRespVO;
import com.aurum.crm.module.crm.model.vo.OpportunitySimpleRespVO;
import com.aurum.crm.module.crm.model.vo.OpportunityStageLogRespVO;
import com.aurum.crm.module.crm.model.StarBizType;
import com.aurum.crm.module.crm.service.OpportunityService;
import com.aurum.crm.module.crm.service.BusinessNumberService;
import com.aurum.crm.module.system.service.OssStorageService;
import com.aurum.crm.module.crm.service.StarService;
import com.aurum.crm.module.crm.support.ItemSupport;
import com.aurum.crm.module.crm.support.MoneyCalculator;
import com.aurum.crm.module.crm.support.SubordinateUserIds;
import com.aurum.crm.mybatis.core.PageParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aurum.crm.module.crm.service.impl.ExportSupport.timeStr;

/**
 * 商机 Service 实现。
 *
 * <p><b>对外字段名对齐前端</b>（{@code crm-ui}）：方法签名与 VO 用
 * {@code name/customerId/ownerUserId/stage/expectDealTime/totalAmount}，
 * 与数据库列的对应关系集中在 {@link OpportunityConvert}，本类里不出现 DO 的原始字段名
 * （只有 wrapper 的条件构造必须用列，这是 MyBatis-Plus 的形态）。对照表见
 * {@code docs/09-商机模块字段契约.md}。
 *
 * <p><b>数据权限不在这里写</b> —— 由 {@code crm-mybatis} 的
 * {@code DataPermissionInnerInterceptor} 统一改写 SQL（{@code crm_opportunity} 在它的白名单里），
 * 规则见《04-权限设计文档》3.3 / 3.4。{@code crm_opportunity.owner_user_id} 是 {@code NOT NULL}
 * （商机没有公海），所以拦截器生成的 {@code owner_user_id IS NULL} 分支恒为 false，
 * 条件自动退化成纯 data_scope。
 *
 * <p><b>写操作的防越权同样靠拦截器</b>：{@link #update} / {@link #delete} / {@link #changeStage}
 * 都先调 {@code getOpportunityDO}（{@code selectById}，拦截器会改写主键查询），
 * 越权时直接查不到、抛「商机不存在」，根本走不到写那一步；而且拦截器对 UPDATE / DELETE
 * 也生效 —— 越权的写会因 WHERE 不匹配而影响 0 行。
 *
 * <p>⚠️ 早期版本这里手写过一份 {@code applyDataScope}，装上拦截器后**必须删掉**，
 * 否则两个条件会 AND 在一起变成双重过滤（客户模块也留了同样的说明）。
 *
 * <p><b>刻意用 {@code LIKE} 而不是全文索引</b>：建表文档 5.1 建议商机名称走
 * {@code MATCH ... AGAINST}，但 ngram 分词器 {@code ngram_token_size} 默认是 2，
 * 单字关键词在 BOOLEAN MODE 下查不到结果。产品模块踩过同一个坑，这里保持一致。
 *
 * <p><b>关于整单折扣</b>：{@code crm_opportunity.discount_rate}（原来是 {@code win_rate}）
 * 由 Convert 从请求的 {@code discount} 直接映射；对外的「整单最终价」是**派生值**
 * （{@code amount × (1 − discount/100)}），不落库，见 {@link #applyDerivedFields}。
 *
 * <p><b>「合计总价」的口径（本轮定稿）</b>：<b>有产品明细时 = Σ 明细行的含税金额</b>
 * —— 读库里已按 3.6 逐行四舍五入的 {@code crm_opportunity_item.amount} 直接加总（不重算），
 * 由 {@link #syncAmountFromItems} 在「追加 / 覆盖明细」之后回写主表；<b>没有明细时</b> =
 * 表单手填的预计金额（先填预估、后补明细的销售习惯保留）。
 *
 * <p>由此带来三条确定的语义（详见 {@code docs/13-商机与报价单金额口径总览.md}）：
 * <ul>
 *     <li>有明细时 {@code PUT /crm/opportunity/{id}} 提交的 {@code totalAmount} 会被明细汇总覆盖
 *       （只记 {@code debug} 日志，**不返回 400** —— 与报价单「客户端提交的金额一律被覆盖」
 *       同一取舍：服务端重算已经保证库里是对的，严格校验只会把用户的保存卡死）</li>
 *     <li>明细被整份清空（{@code PUT /{id}/products} 传 {@code []}）时 {@code amount}
 *       **保留原值、不静默清零** —— 退回手填模式，用户可在编辑表单里重填</li>
 *     <li>{@code amount} 从此是**含税**口径（= 明细的价税合计），与报价单的
 *       {@code total_with_tax} 同基数；报价单的 {@code totalAmount} 是**不含税**，两者不可直接比</li>
 * </ul>
 */
@Slf4j
@Service
public class OpportunityServiceImpl implements OpportunityService {

    /** 商机编号前缀，与初始化数据的 {@code SJ20260001} 保持同一前缀 */
    private static final String OPPORTUNITY_NO_PREFIX = "SJ";

    /** 阶段变更来源：1 人工（建表文档：1人工 2AI） */
    private static final Integer STAGE_SOURCE_MANUAL = 1;

    /** 从编辑弹窗改阶段时写进变更历史的说明（用户没地方填原因，只能由服务端生成） */
    private static final String STAGE_REASON_FROM_EDIT = "编辑商机时修改阶段";

    /** 筛选胶囊：我负责的 */
    private static final String SCOPE_MINE = "mine";

    /** 筛选胶囊：下属负责的（负责人 ∈ 沿 superior_id 递归取到的全部下属，不含自己） */
    private static final String SCOPE_SUBORDINATE = "subordinate";

    /** 筛选胶囊：我关注的（依赖的关注表尚未建表，本轮不额外筛选） */
    private static final String SCOPE_STARRED = "starred";

    /** 折扣上限，用于 Service 层的范围校验（与 Controller 的 {@code @DecimalMax} 一致） */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * 「合计总价」的列容量上限。
     *
     * <p>{@code crm_opportunity.amount} 是 {@code DECIMAL(15,2)}，对外的
     * {@code OpportunitySaveReqVO.totalAmount} 也是 {@code @Digits(integer = 13, fraction = 2)} ——
     * 上限是 13 位整数 = {@code 9999999999999.99}。
     *
     * <p>为什么需要它：明细汇总**可能超出**这个容量（单价与数量各自都合法，乘积不一定），
     * 不拦的话 MySQL 会因超范围而报错（500），用户只看到「服务器异常」。
     */
    private static final BigDecimal AMOUNT_MAX = new BigDecimal("9999999999999.99");

    /**
     * 漏斗固定输出行。输单是终态、概率 0，不属于「越往下越窄」的推进过程，
     * 前端 {@code FunnelChart} 的配色也只给了 5 种，所以恒定五行。
     */
    private static final List<OpportunityStage> FUNNEL_STAGES = List.of(
            OpportunityStage.INITIAL_CONTACT,
            OpportunityStage.NEED_CONFIRMED,
            OpportunityStage.SOLUTION_QUOTED,
            OpportunityStage.NEGOTIATING,
            OpportunityStage.WON);

    @Resource
    private OpportunityMapper opportunityMapper;
    @Resource
    private OpportunityStageLogMapper opportunityStageLogMapper;
    @Resource
    private OpportunityItemMapper opportunityItemMapper;
    @Resource
    private CustomerMiniMapper customerMiniMapper;
    @Resource
    private SysUserMiniMapper sysUserMiniMapper;
    @Resource
    private OpportunityConvert opportunityConvert;
    /** 明细行的共用机制（与报价单明细共用一份，见 ItemSupport 的类注释） */
    @Resource
    private ItemSupport itemSupport;
    @Resource
    private OssStorageService ossStorageService;
    @Resource
    private BusinessNumberService businessNumberService;

    @Resource
    private StarService starService;
    /** 「我的全部下属」—— {@code scope=subordinate} 胶囊用，口径见 SubordinateUserIds 的类注释 */
    @Resource
    private SubordinateUserIds subordinateUserIds;

    // ==================== 查询 ====================

    @Override
    public PageResult<OpportunityRespVO> pageQuery(OpportunityPageReqVO reqVO) {
        LoginUser loginUser = LoginUserHolder.getRequired();
        String scope = normalizeScope(reqVO.getScope());

        // 「下属负责的商机」：数据范围只有「仅本人」的用户不放行，明确返回空集。
        // 即使真的被别人配了下属（sys_user.superior_id 指错人）也仍然空集 ——
        // 「仅本人」的销售本来就不该看到别人的商机，这条守卫与拦截器的 fail-closed 取向一致。
        // 不能退化成「按本部门查」—— 那会把同部门同事的商机暴露给销售专员（越权）。
        if (SCOPE_SUBORDINATE.equals(scope) && isSelfOnlyScope(loginUser)) {
            return emptyResult(reqVO);
        }

        LambdaQueryWrapper<OpportunityDO> wrapper = Wrappers.lambdaQuery();
        applyKeyword(wrapper, reqVO.getKeyword());
        if (StringUtils.hasText(reqVO.getStage())) {
            wrapper.eq(OpportunityDO::getStage, resolveStage(reqVO.getStage()).getCode());
        }
        if (reqVO.getCustomerId() != null) {
            wrapper.eq(OpportunityDO::getCustomerId, reqVO.getCustomerId());
        }
        if (reqVO.getOwnerUserId() != null) {
            wrapper.eq(OpportunityDO::getOwnerUserId, reqVO.getOwnerUserId());
        }
        if (reqVO.getCreateTimeStart() != null) {
            wrapper.ge(OpportunityDO::getCreateTime, reqVO.getCreateTimeStart());
        }
        if (reqVO.getCreateTimeEnd() != null) {
            wrapper.le(OpportunityDO::getCreateTime, reqVO.getCreateTimeEnd());
        }
        // 数据权限不在这里拼 —— 由 crm-mybatis 的 DataPermissionInnerInterceptor
        // 统一改写 SQL（crm_opportunity 在它的白名单里）。早期这里手写过一份 applyDataScope，
        // 装上拦截器后必须删掉，否则两个条件会 AND 在一起变成双重过滤。
        applyScopeCapsule(wrapper, scope, loginUser);
        // 默认列表排序：最近创建在前（走 idx_create_time），带主键保证分页稳定
        wrapper.orderByDesc(OpportunityDO::getCreateTime)
                .orderByDesc(OpportunityDO::getOpportunityId);

        Page<OpportunityDO> page = opportunityMapper.selectPage(reqVO, wrapper);
        return toPageResult(page);
    }

    @Override
    public OpportunityRespVO get(Long id) {
        OpportunityDO opportunityDO = getOpportunityDO(id);
        List<OpportunityItemDO> items = loadItems(id);
        OpportunityRespVO vo = opportunityConvert.toRespVO(opportunityDO);
        vo.setCustomerName(loadCustomerName(opportunityDO.getCustomerId()));
        vo.setOwnerName(loadUserName(opportunityDO.getOwnerUserId()));
        vo.setCreator(loadUserName(opportunityDO.getCreateBy()));
        // 单条查关注态（列表走 starredBizIds 批量查，见 toPageResult）
        vo.setStarred(starService.isStarred(StarBizType.OPPORTUNITY, id));
        fillItems(vo, items, itemSupport.loadUnitNames(unitIdsOf(items)));
        applyDerivedFields(vo, items);
        return vo;
    }

    @Override
    public PageResult<OpportunitySimpleRespVO> simpleList(OpportunitySimplePageReqVO reqVO) {
        LambdaQueryWrapper<OpportunityDO> wrapper = Wrappers.lambdaQuery();
        // 只取选择器真正要读的四列：省流量，也顺带避免把备注这类自由文本读出来
        wrapper.select(OpportunityDO::getOpportunityId,
                OpportunityDO::getOpportunityName,
                OpportunityDO::getCustomerId,
                OpportunityDO::getOwnerUserId);
        if (StringUtils.hasText(reqVO.getKeyword())) {
            wrapper.like(OpportunityDO::getOpportunityName, reqVO.getKeyword().trim());
        }
        // 选择器同样受数据权限约束（由拦截器改写 SQL）：看不到的商机不该出现在选择器里
        wrapper.orderByDesc(OpportunityDO::getCreateTime)
                .orderByDesc(OpportunityDO::getOpportunityId);

        Page<OpportunityDO> page = opportunityMapper.selectPage(reqVO, wrapper);
        List<OpportunityDO> records = page.getRecords();
        Map<Long, String> customerNames = customerNameMap(records);
        Map<Long, String> ownerNames = userNameMap(records.stream()
                .map(OpportunityDO::getOwnerUserId).toList());

        // 明细随选择器一起下发：合同表单选中商机后要拿它做两件事 ——
        // ① 带出客户（避免用户再手选一遍、也避免选成别家的客户）；
        // ② 该商机**没有报价单**时，产品回退成商机自己的明细（「商机直签合同」也带得出产品）。
        // 按这一页的商机 id 批量查明细 + 批量查单位名，不做 N+1。
        Map<Long, List<OpportunityItemDO>> itemsByOpportunity = loadItems(records.stream()
                .map(OpportunityDO::getOpportunityId).toList());
        List<OpportunityItemDO> allItems = itemsByOpportunity.values().stream()
                .flatMap(List::stream)
                .toList();
        Map<Long, String> unitNames = itemSupport.loadUnitNames(unitIdsOf(allItems));

        List<OpportunitySimpleRespVO> list = records.stream().map(opportunity -> {
            OpportunitySimpleRespVO vo = opportunityConvert.toSimpleRespVO(opportunity);
            vo.setCustomerName(customerNames.get(opportunity.getCustomerId()));
            vo.setOwnerName(ownerNames.get(opportunity.getOwnerUserId()));
            vo.setProducts(itemRespVOs(
                    itemsByOpportunity.getOrDefault(opportunity.getOpportunityId(), Collections.emptyList()),
                    unitNames));
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public List<OpportunityStageLogRespVO> listStageLogs(Long id) {
        // 商机不存在直接抛 2201，而不是返回空数组
        getOpportunityDO(id);

        List<OpportunityStageLogDO> logs = opportunityStageLogMapper.selectList(
                Wrappers.<OpportunityStageLogDO>lambdaQuery()
                        .eq(OpportunityStageLogDO::getOpportunityId, id)
                        // 最近一次变更排在最前（历史列表的常规读法）；
                        // 带主键排序是为了同一秒内的多条记录顺序稳定
                        .orderByDesc(OpportunityStageLogDO::getCreateTime)
                        .orderByDesc(OpportunityStageLogDO::getLogId));

        Map<Long, String> operatorNames = userNameMap(logs.stream()
                .map(OpportunityStageLogDO::getOperatorId).toList());
        return logs.stream().map(logDO -> {
            OpportunityStageLogRespVO vo = opportunityConvert.toStageLogRespVO(logDO);
            vo.setOperator(operatorNames.get(logDO.getOperatorId()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<OpportunityFunnelRespVO> funnel() {
        LambdaQueryWrapper<OpportunityDO> wrapper = Wrappers.lambdaQuery();
        wrapper.select(OpportunityDO::getStage, OpportunityDO::getAmount);
        // 行级数据权限由拦截器改写 SQL，这里不用（也不该）再拼一遍

        // 在内存里分组，而不是写成 SELECT stage, COUNT(*), SUM(amount) ... GROUP BY stage。
        // 两种写法都正确 —— 行级数据权限由 DataPermissionInnerInterceptor 统一改写 SQL，
        // 与本查询怎么拼 wrapper 无关。这里保留内存分组是因为：
        //   ① 输出形状是「固定 5 行、没数据也要返回 0」，本来就要在 Java 侧补齐；
        //   ② 首页看板的一次性统计，在商机这个量级上多一次列扫描可以忽略。
        // 等商机量级上来，换成一句 GROUP BY 即可，返回结构不变。
        List<OpportunityDO> rows = opportunityMapper.selectList(wrapper);

        Map<Integer, Long> countByStage = new HashMap<>();
        Map<Integer, BigDecimal> amountByStage = new HashMap<>();
        for (OpportunityDO row : rows) {
            if (row.getStage() == null) {
                continue;
            }
            countByStage.merge(row.getStage(), 1L, Long::sum);
            amountByStage.merge(row.getStage(),
                    row.getAmount() == null ? BigDecimal.ZERO : row.getAmount(),
                    BigDecimal::add);
        }
        return FUNNEL_STAGES.stream()
                .map(stage -> new OpportunityFunnelRespVO(
                        stage.getLabel(),
                        countByStage.getOrDefault(stage.getCode(), 0L),
                        amountByStage.getOrDefault(stage.getCode(), BigDecimal.ZERO)))
                .collect(Collectors.toList());
    }

    @Override
    public String export(OpportunityPageReqVO reqVO) {
        return ExportSupport.toUrl(ossStorageService, buildExportRows(reqVO), OpportunityExportRowVO.class);
    }

    @Override
    public String exportXml(OpportunityPageReqVO reqVO) {
        return ExportSupport.toXmlUrl(ossStorageService, buildExportRows(reqVO), OpportunityExportRowVO.class);
    }

    /** 复用 pageQuery 的过滤条件（含 scope 胶囊与数据权限），但不翻页 */
    private List<OpportunityExportRowVO> buildExportRows(OpportunityPageReqVO reqVO) {
        LoginUser loginUser = LoginUserHolder.getRequired();
        String scope = normalizeScope(reqVO.getScope());
        if (SCOPE_SUBORDINATE.equals(scope) && isSelfOnlyScope(loginUser)) {
            return List.of();
        }

        LambdaQueryWrapper<OpportunityDO> wrapper = Wrappers.lambdaQuery();
        applyKeyword(wrapper, reqVO.getKeyword());
        if (StringUtils.hasText(reqVO.getStage())) {
            wrapper.eq(OpportunityDO::getStage, resolveStage(reqVO.getStage()).getCode());
        }
        if (reqVO.getCustomerId() != null) {
            wrapper.eq(OpportunityDO::getCustomerId, reqVO.getCustomerId());
        }
        if (reqVO.getOwnerUserId() != null) {
            wrapper.eq(OpportunityDO::getOwnerUserId, reqVO.getOwnerUserId());
        }
        if (reqVO.getCreateTimeStart() != null) {
            wrapper.ge(OpportunityDO::getCreateTime, reqVO.getCreateTimeStart());
        }
        if (reqVO.getCreateTimeEnd() != null) {
            wrapper.le(OpportunityDO::getCreateTime, reqVO.getCreateTimeEnd());
        }
        applyScopeCapsule(wrapper, scope, loginUser);
        wrapper.orderByDesc(OpportunityDO::getCreateTime)
                .orderByDesc(OpportunityDO::getOpportunityId);

        List<OpportunityDO> records = opportunityMapper.selectList(wrapper);
        Map<Long, String> customerNames = customerNameMap(records);
        Map<Long, String> ownerNames = userNameMap(records.stream()
                .map(OpportunityDO::getOwnerUserId).toList());

        return records.stream().map(o -> {
            OpportunityExportRowVO row = new OpportunityExportRowVO();
            row.setOpportunityNo(o.getOpportunityNo());
            row.setOpportunityName(o.getOpportunityName());
            row.setCustomerName(customerNames.get(o.getCustomerId()));
            row.setAmount(o.getAmount() == null ? "" : o.getAmount().toPlainString());
            row.setStage(OpportunityStage.labelOf(o.getStage()));
            row.setOwnerName(ownerNames.get(o.getOwnerUserId()));
            row.setSource(DictLabels.source(o.getSource()));
            row.setExpectedCloseDate(o.getExpectedCloseDate() == null ? "" : o.getExpectedCloseDate().toString());
            row.setCreateTime(timeStr(o.getCreateTime()));
            return row;
        }).collect(Collectors.toList());
    }

    // ==================== 写入 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OpportunitySaveReqVO reqVO) {
        LoginUser loginUser = LoginUserHolder.getRequired();
        requireCustomerExists(reqVO.getCustomerId());

        // 负责人缺省为当前登录人（与客户模块同一口径）
        Long ownerUserId = reqVO.getOwnerUserId() != null ? reqVO.getOwnerUserId() : loginUser.getUserId();
        SysUserMiniDO owner = requireUserExists(ownerUserId);

        OpportunityStage stage = resolveStageOrDefault(reqVO.getStage());

        OpportunityDO opportunityDO = opportunityConvert.toDO(reqVO);
        opportunityDO.setOpportunityId(null);
        opportunityDO.setOpportunityNo(buildOpportunityNo(reqVO.getCustomerId(), owner.getDeptId()));
        opportunityDO.setOwnerUserId(ownerUserId);
        // 归属部门跟着【负责人】走，不跟创建人 —— 数据权限是按 owner / dept 过滤的，
        // 部门若跟创建人走，主管替下属录一条商机，这条商机主管自己反而看不到。
        opportunityDO.setDeptId(owner == null ? loginUser.getDeptId() : owner.getDeptId());
        opportunityDO.setStage(stage.getCode());
        // 整单折扣：缺省 0，并在这里做一次范围校验。显式兜底而不是依赖列默认值 ——
        // 真库这一列是可空的（NULL），写 0 出去能让「不打折」有一个确定的值，读路径也不必再兜。
        BigDecimal discount = opportunityDO.getDiscountRate() == null
                ? BigDecimal.ZERO : opportunityDO.getDiscountRate();
        validateDiscount(discount);
        opportunityDO.setDiscountRate(discount);
        opportunityDO.setExpectedCloseDate(parseDateLeniently(reqVO.getExpectDealTime()));
        opportunityDO.setNextContactTime(parseDateLeniently(reqVO.getNextContactTime()));
        // 直接以「赢单」建单时补上实际成交日期，与 changeStage 的口径一致
        opportunityDO.setActualCloseDate(stage == OpportunityStage.WON ? LocalDate.now() : null);
        if (opportunityDO.getAmount() == null) {
            opportunityDO.setAmount(BigDecimal.ZERO);
        }

        opportunityMapper.insert(opportunityDO);
        // 新增刻意**不写**阶段变更日志：历史记录记的是「变更」，
        // 建单时的初始阶段已经在商机本身上了，补一条 from_stage=NULL 的记录只会让历史变噪。
        // （sql/crm.sql 的初始化数据也是同一口径：日志从第 2 个阶段才开始记。）
        log.info("[商机] 新增成功：id={}, no={}, stage={}",
                opportunityDO.getOpportunityId(), opportunityDO.getOpportunityNo(), stage.getLabel());
        return opportunityDO.getOpportunityId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, OpportunitySaveReqVO reqVO) {
        OpportunityDO existing = getOpportunityDO(id);
        requireCustomerExists(reqVO.getCustomerId());

        OpportunityDO update = opportunityConvert.toDO(reqVO);
        update.setOpportunityId(id);
        // 折扣同样要校验（null = 不传 = 不改，由 Convert + not_null 策略保证）
        validateDiscount(reqVO.getDiscount());

        // 合计总价：**有明细时以明细为准**（前端那一格此时是只读的，这里是防漂移的第二道保险）。
        // 无明细时不动这一列 —— 提交什么就是什么（手填预估模式），靠全局 update-strategy=not_null。
        List<OpportunityItemDO> existingItems = loadItems(id);
        BigDecimal itemsAmount = itemsAmountOf(existingItems);
        if (itemsAmount != null) {
            validateAmount(itemsAmount);
            if (reqVO.getTotalAmount() != null && reqVO.getTotalAmount().compareTo(itemsAmount) != 0) {
                log.debug("[商机] 该商机有 {} 行明细，提交的合计总价 {} 已被明细汇总覆盖：id={}, amount={}",
                        existingItems.size(), reqVO.getTotalAmount(), id, itemsAmount);
            }
            update.setAmount(itemsAmount);
        }

        // 负责人：前端传了才改；改了要同步 dept_id（理由同 create）
        if (reqVO.getOwnerUserId() != null) {
            SysUserMiniDO owner = requireUserExists(reqVO.getOwnerUserId());
            update.setOwnerUserId(reqVO.getOwnerUserId());
            update.setDeptId(owner == null ? null : owner.getDeptId());
        }

        // 阶段：编辑弹窗里也能改阶段，这里必须与 changeStage 同样处理，
        // 否则用户从「编辑」改的阶段在「阶段变更历史」里查不到 —— 历史断层比缺字段更难发现。
        // ⚠️ 阶段变更**不再动任何概率/折扣字段**：win_rate 已改成 discount_rate（整单折扣），
        //    折扣与阶段无关；折扣走 Convert 的 discount → discount_rate 直接映射
        //    （不传就不改，靠全局 update-strategy=not_null）。
        OpportunityStage targetStage = null;
        if (StringUtils.hasText(reqVO.getStage())) {
            targetStage = resolveStage(reqVO.getStage());
            update.setStage(targetStage.getCode());
            if (targetStage == OpportunityStage.WON && existing.getActualCloseDate() == null) {
                update.setActualCloseDate(LocalDate.now());
            }
        }

        // 预计成交时间：null = 前端没传这一项（不改这一列），空串 = 用户清空了它
        LocalDate expectedCloseDate = parseDateLeniently(reqVO.getExpectDealTime());
        update.setExpectedCloseDate(expectedCloseDate);

        // 下次联系时间：与预计成交时间同一套语义
        LocalDate nextContactTime = parseDateLeniently(reqVO.getNextContactTime());
        update.setNextContactTime(nextContactTime);

        opportunityMapper.updateById(update);

        // 清空日期要单独发一条：全局 update-strategy=not_null 会把 null 当成「不改这一列」，
        // 而「用户把已填的日期删掉」表达的恰恰是「改成 NULL」。显式 set 不受 not_null 影响。
        // 之所以放在 updateById 之后而不是合成一条 update(entity, wrapper)：
        // 前者依赖的字段自动填充（updateBy/updateTime）行为是确定的，不必赌 ORM 的参数改写。
        if (reqVO.getExpectDealTime() != null && expectedCloseDate == null) {
            opportunityMapper.update(null, Wrappers.<OpportunityDO>lambdaUpdate()
                    .eq(OpportunityDO::getOpportunityId, id)
                    .set(OpportunityDO::getExpectedCloseDate, null));
        }
        if (reqVO.getNextContactTime() != null && nextContactTime == null) {
            opportunityMapper.update(null, Wrappers.<OpportunityDO>lambdaUpdate()
                    .eq(OpportunityDO::getOpportunityId, id)
                    .set(OpportunityDO::getNextContactTime, null));
        }

        if (targetStage != null && !targetStage.getCode().equals(existing.getStage())) {
            writeStageLog(id, existing.getStage(), targetStage, STAGE_REASON_FROM_EDIT);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getOpportunityDO(id);
        // 逻辑删除。阶段变更历史不跟着删：它是审计流水，商机删了仍应可追溯
        opportunityMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void star(Long id) {
        // 先做存在性校验：不存在的商机不能关注 → 2201。
        // 这一步同时继承了数据权限保护（selectById 被拦截器改写），越权关注也是 2201
        getOpportunityDO(id);
        starService.star(StarBizType.OPPORTUNITY, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unstar(Long id) {
        getOpportunityDO(id); // 与 delete / star 一致，先做存在性校验
        starService.unstar(StarBizType.OPPORTUNITY, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStage(Long id, OpportunityStageChangeReqVO reqVO) {
        OpportunityDO existing = getOpportunityDO(id);
        OpportunityStage target = resolveStage(reqVO.getStage());

        // 幂等：目标阶段与当前一致时不写历史。
        // 前端弹窗已经把当前阶段从选项里剔掉了，但接口是可以被直接调用的，
        // 不拦的话会留下一条「A → A」的噪声记录。
        if (target.getCode().equals(existing.getStage())) {
            log.debug("[商机] 阶段未变化，跳过写变更历史：id={}, stage={}", id, target.getLabel());
            return;
        }

        OpportunityDO update = new OpportunityDO();
        update.setOpportunityId(id);
        update.setStage(target.getCode());
        // 阶段变更**不动整单折扣**：折扣是商务条件，与阶段推进无关
        //（win_rate 那套「按阶段落概率」的逻辑已随改名一起删除）。
        // 赢单时落实际成交日期（与 expected_close_date 配对，用于评估预测准确率，见建表文档 3.x）。
        // 只在首次赢单时写：先赢单再改阶段（如撤销）不会把它抹掉，历史成交事实要留痕。
        if (target == OpportunityStage.WON && existing.getActualCloseDate() == null) {
            update.setActualCloseDate(LocalDate.now());
        }
        opportunityMapper.updateById(update);

        writeStageLog(id, existing.getStage(), target, reqVO.getRemark());
        log.info("[商机] 阶段变更：id={}, {} -> {}", id,
                OpportunityStage.labelOf(existing.getStage()), target.getLabel());
    }

    // ==================== 产品明细 ====================

    /**
     * <b>追加</b>明细。
     *
     * <p>新行的 {@code sort} 从「现有最大 sort + 1」开始，**不动已有行**；
     * 因此行数上限校验的是「已有 + 新增」的总数。
     *
     * <p>写完之后按「已有 + 新增」的全量明细回写主表的「合计总价」（见
     * {@link #syncAmountFromItems}）—— 这是「明细生效」的落点，同一事务内完成。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addProducts(Long id, List<DocumentItemSaveReqVO> reqVO) {
        OpportunityDO existing = getOpportunityDO(id);
        List<DocumentItemSaveReqVO> rows = reqVO == null ? List.of() : reqVO;
        if (rows.isEmpty()) {
            // 追加空数组 = 什么都不做（幂等），不报错 —— 前端与 curl 都可能这么调
            return;
        }
        List<OpportunityItemDO> existingItems = loadItems(id);
        itemSupport.validateCount(existingItems.size() + rows.size(), "单张商机");

        // 续号而不是从 1 重来：否则新行会插到旧行中间，详情页的明细顺序看起来就乱了
        int startSort = existingItems.stream()
                .map(OpportunityItemDO::getSort)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        List<OpportunityItemDO> items = itemSupport.buildItems(rows, startSort, OpportunityItemDO::new, "商机");
        insertItems(id, items);

        // 合计总价 = 全量明细（已有 + 新增）的含税金额汇总
        List<OpportunityItemDO> allItems = new ArrayList<>(existingItems.size() + items.size());
        allItems.addAll(existingItems);
        allItems.addAll(items);
        syncAmountFromItems(existing, allItems, "追加产品明细");

        log.info("[商机] 追加产品明细：opportunityId={}, 新增{}行（原{}行）", id, items.size(), existingItems.size());
    }

    /**
     * <b>整份覆盖</b>明细：先逻辑删除旧行（留痕）再插入新行 —— 与报价单同一语义。
     *
     * <p>前端详情页的「添加产品」弹窗回填现有明细，用户在里面增删改后保存走的就是这里。
     *
     * <p>覆盖之后同样回写「合计总价」；<b>请求为空数组时不清零</b> ——
     * 明细被清空即退回「手填预估」模式，静默把金额抹成 0 比留着旧值更危险。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceProducts(Long id, List<DocumentItemSaveReqVO> reqVO) {
        OpportunityDO existing = getOpportunityDO(id);
        List<DocumentItemSaveReqVO> rows = reqVO == null ? List.of() : reqVO;
        itemSupport.validateCount(rows.size(), "单张商机");

        // 先把新行建出来：如果请求里有非法数据（如超长名称），在第 5 步之前就会 400，
        // 不会出现「旧行已删、新行没插进来」的半截状态
        List<OpportunityItemDO> items = itemSupport.buildItems(rows, 1, OpportunityItemDO::new, "商机");

        opportunityItemMapper.delete(Wrappers.<OpportunityItemDO>lambdaQuery()
                .eq(OpportunityItemDO::getOpportunityId, id));
        insertItems(id, items);
        syncAmountFromItems(existing, items, "覆盖产品明细");
        log.info("[商机] 覆盖产品明细：opportunityId={}, 新{}行", id, items.size());
    }

    /** 批量插入明细（行数有 {@code ItemSupport.MAX_ITEMS} 上限，逐条 insert 足够） */
    private void insertItems(Long opportunityId, List<OpportunityItemDO> items) {
        for (OpportunityItemDO item : items) {
            item.setItemId(null);
            item.setOpportunityId(opportunityId);
            opportunityItemMapper.insert(item);
        }
    }

    /** 取单个商机的明细，按 {@code sort} 升序（同序按主键，保证顺序稳定） */
    private List<OpportunityItemDO> loadItems(Long opportunityId) {
        return opportunityItemMapper.selectList(Wrappers.<OpportunityItemDO>lambdaQuery()
                .eq(OpportunityItemDO::getOpportunityId, opportunityId)
                .orderByAsc(OpportunityItemDO::getSort)
                .orderByAsc(OpportunityItemDO::getItemId));
    }

    /** 批量取明细，按 {@code opportunity_id} 分组（列表页用，避免 N+1） */
    private Map<Long, List<OpportunityItemDO>> loadItems(Collection<Long> opportunityIds) {
        List<Long> ids = opportunityIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            // ⚠️ 空集合会让 MP 生成非法的 IN ()；空 Map 必须用 Collections.emptyMap()
            return Collections.emptyMap();
        }
        return opportunityItemMapper.selectList(Wrappers.<OpportunityItemDO>lambdaQuery()
                        .in(OpportunityItemDO::getOpportunityId, ids)
                        .orderByAsc(OpportunityItemDO::getSort)
                        .orderByAsc(OpportunityItemDO::getItemId))
                .stream()
                .collect(Collectors.groupingBy(OpportunityItemDO::getOpportunityId));
    }

    /** 明细 DO → VO（补上 {@code unitName}，编辑器的单位下拉靠它回填） */
    private void fillItems(OpportunityRespVO vo, List<OpportunityItemDO> items, Map<Long, String> unitNames) {
        vo.setProducts(itemRespVOs(items, unitNames));
    }

    /**
     * 明细 DO → 响应行（按 {@code unitId} 补单位名）。
     *
     * <p>详情 / 列表（{@link #fillItems}）与选择器（{@link #simpleList}）共用这一份：
     * 补值逻辑写两遍的话，改了一处、另一处会安静地少一个单位名 ——
     * 而症状只是「编辑时单位下拉是空的」，很难联想到是这里。
     *
     * @param unitNames 批量查到的 {@code unitId → unitName}；查不到时该行 {@code unitName} 为 null
     */
    private List<DocumentItemRespVO> itemRespVOs(List<OpportunityItemDO> items, Map<Long, String> unitNames) {
        return items.stream().map(item -> {
            DocumentItemRespVO itemVO = opportunityConvert.toItemRespVO(item);
            itemVO.setUnitName(unitNames.get(item.getUnitId()));
            return itemVO;
        }).toList();
    }

    private List<Long> unitIdsOf(List<OpportunityItemDO> items) {
        return items.stream().map(OpportunityItemDO::getUnitId).filter(Objects::nonNull).distinct().toList();
    }

    // ==================== 内部方法：查询条件 ====================

    /**
     * 关键字：同时匹配商机名称或负责人姓名（前端搜索框占位就是「商机名称/负责人」）。
     *
     * <p>负责人姓名在 {@code sys_user} 里，先按姓名把命中的用户 ID 查出来再 {@code IN}，
     * 避免在商机表上写子查询。
     */
    private void applyKeyword(LambdaQueryWrapper<OpportunityDO> wrapper, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String kw = keyword.trim();
        List<Long> ownerIds = sysUserMiniMapper.selectList(
                        Wrappers.<SysUserMiniDO>lambdaQuery().like(SysUserMiniDO::getRealName, kw))
                .stream()
                .map(SysUserMiniDO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ownerIds.isEmpty()) {
            wrapper.like(OpportunityDO::getOpportunityName, kw);
            return;
        }
        // 括号要一起加：否则 LIKE 的 OR 会与后面的数据权限条件平级展开，
        // 变成「名称命中 或 负责人命中 或 部门命中」，等于把数据权限绕过去了。
        wrapper.and(w -> w.like(OpportunityDO::getOpportunityName, kw)
                .or().in(OpportunityDO::getOwnerUserId, ownerIds));
    }

    /** 私海收窄：只看自己负责的（供 {@code scope=mine} 用）。 */
    private void applyOwnerScope(LambdaQueryWrapper<OpportunityDO> wrapper, LoginUser loginUser) {
        if (loginUser.getUserId() != null) {
            wrapper.eq(OpportunityDO::getOwnerUserId, loginUser.getUserId());
        }
    }

    /**
     * 前端筛选胶囊（{@code ProList} 的 {@code scope}）。
     *
     * <p>这里只处理**数据范围以外的 UI 筛选**；行级数据权限由拦截器负责（见类注释）。
     * 胶囊与数据权限是**叠加（AND）**关系：胶囊只会进一步收窄，绝不会放宽 ——
     * 所以即使前端传了越权的 scope，也看不到数据权限之外的数据。
     */
    private void applyScopeCapsule(LambdaQueryWrapper<OpportunityDO> wrapper, String scope, LoginUser loginUser) {
        if (scope == null) {
            return;
        }
        switch (scope) {
            case SCOPE_MINE -> applyOwnerScope(wrapper, loginUser);
            case SCOPE_SUBORDINATE -> {
                // 「下属负责的商机」= 负责人落在「我的全部下属」集合里
                //（沿 sys_user.superior_id 逐级向下，**多层且不含自己**，口径见《05-接口文档》4.1）。
                //
                // ⚠️ 这里刻意【不】按 dept_id / dataScopeDeptIds 过滤。本项目的上级链与部门树并不等价
                //    （种子数据里销售总监(2) 与张三/李四(3/4) 同属部门 2），按部门查会把**同部门同事**
                //    的商机、以及**我自己**的商机一并带出来 —— 那正是「销售不应看到他人私海数据」
                //    （《04-权限设计文档》决策 1）要拦的事。下属是「我」的下属，不是「部门」的属性。
                List<Long> subordinateIds = subordinateUserIds.of(loginUser.getUserId());
                if (subordinateIds.isEmpty()) {
                    // 没有下级（或 userId 取不到）→ 收窄到空集。
                    // 这里**不能**走下面 default 的「不筛」策略，也**不能**退化成「本部门查」：
                    // 前者会把全量数据当成「下属负责的」返回，后者会把同事甚至我自己的商机带出来。
                    // `1 = 0` 的写法与 DataPermissionInnerInterceptor.NEVER_MATCH 一致。
                    // 同时这也是「不给 MyBatis-Plus 传空集合」的守卫：3.5.7 的 in(col, empty)
                    // 会生成非法的 IN ()。
                    wrapper.apply("1 = 0");
                    return;
                }
                wrapper.in(OpportunityDO::getOwnerUserId, subordinateIds);
            }
            case SCOPE_STARRED -> {
                // 我关注的 = 我关注过的商机（跨归属，所以按 opportunity_id 而不是 owner 过滤）
                //
                // ⚠️ 这里曾经是**静默不筛**：注释写着「关注表尚未建表」，但那句话早就过时了 ——
                //    crm_star 表、StarService、StarBizType.OPPORTUNITY 都在，线索与客户模块
                //    一直在正常使用。结果是商机列表的「我关注的商机」胶囊点了没反应，
                //    还返回未经收窄的数据（用户会以为筛选生效了）。现已改为真筛选。
                Set<Long> starredIds = starService.starredBizIds(StarBizType.OPPORTUNITY);
                if (starredIds.isEmpty()) {
                    // 一条都没关注 → 收窄到空集。
                    // 这里**不能**走下面 default 的「不筛」策略：那会把未收窄的全量数据当成
                    // 「我关注的」结果返回，用户看不出筛选没生效（正是要修的那个毛病）。
                    // `1 = 0` 的写法与 DataPermissionInnerInterceptor.NEVER_MATCH 一致。
                    wrapper.apply("1 = 0");
                    return;
                }
                wrapper.in(OpportunityDO::getOpportunityId, starredIds);
            }
            // all 与未知取值：不额外筛选。
            // 未知取值「不筛」而不是「筛空」—— 前端以后多加一个胶囊值时，
            // 最坏是多显示几条，而不是整个列表变空且没人知道为什么（产品模块同一取舍）。
            default -> {
            }
        }
    }

    /** 筛选胶囊取值归一化：去掉空白，转小写；未知值原样返回（由调用方按「不筛」处理） */
    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return null;
        }
        // ⚠️ 用 Locale.ROOT 而不是默认 Locale：土耳其语环境下 "I".toLowerCase() 会变成 "ı"，
        //    MINE / STARred 这类取值会莫名匹配不上
        return scope.trim().toLowerCase(Locale.ROOT);
    }

    /** 数据范围是否只有「仅本人」（含取不到的情况，拦截器那边同样按「仅本人」兜底） */
    private boolean isSelfOnlyScope(LoginUser loginUser) {
        Integer dataScope = loginUser.getDataScope();
        return dataScope == null || DataScope.SELF.equals(dataScope);
    }

    // ==================== 内部方法：写入辅助 ====================

    /**
     * 写一条阶段变更日志。
     *
     * @param fromStage 原阶段码值（首次为 null）
     * @param target    目标阶段
     * @param reason    变更原因，可为空
     */
    private void writeStageLog(Long opportunityId, Integer fromStage, OpportunityStage target, String reason) {
        OpportunityStageLogDO logDO = new OpportunityStageLogDO();
        logDO.setOpportunityId(opportunityId);
        logDO.setFromStage(fromStage);
        logDO.setToStage(target.getCode());
        // ⚠️ 恒写 0：这一列是「变更后赢单概率(%)」，而赢单概率业务已随
        //    crm_opportunity.win_rate → discount_rate 的改名一起消失；列本身仍是 NOT NULL，
        //    所以必须给个值。以后该列若被删掉，删这一行即可。
        logDO.setWinRate(BigDecimal.ZERO);
        logDO.setChangeReason(StringUtils.hasText(reason) ? reason.trim() : null);
        // 本轮只有人工变更；AI 预测落地后这里写 2，并同时填 ai_confidence
        logDO.setSource(STAGE_SOURCE_MANUAL);
        logDO.setOperatorId(LoginUserHolder.getUserId());
        // create_time 由 DefaultDBFieldHandler 自动填充，不要手动赋值
        opportunityStageLogMapper.insert(logDO);
    }

    /** 商机编号由 {@code OPP} 编号规则生成。 */
    private String buildOpportunityNo(Long customerId, Long deptId) {
        Map<String, Object> context = new HashMap<>();
        context.put("customerId", customerId);
        if (deptId != null) context.put("deptId", deptId);
        return businessNumberService.next("OPP", context);
    }

    /** 中文标签 → 阶段，非法取值抛 400 并列出可填的值 */
    private OpportunityStage resolveStage(String label) {
        OpportunityStage stage = OpportunityStage.ofLabel(label);
        if (stage == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "商机阶段不合法，请填写：" + OpportunityStage.labelOptions());
        }
        return stage;
    }

    /** 中文标签 → 阶段，空白时取默认阶段「初步接洽」（与前端空表单的默认值一致） */
    private OpportunityStage resolveStageOrDefault(String label) {
        if (!StringUtils.hasText(label)) {
            return OpportunityStage.DEFAULT;
        }
        return resolveStage(label);
    }

    /**
     * 宽容解析日期。
     *
     * <p>为什么收字符串而不是直接收 {@code LocalDate}：见
     * {@link OpportunitySaveReqVO} 的类注释 —— 前端从没选过日期时提交的是**空字符串**，
     * 而 Jackson 会把 {@code ""} 交给 {@code LocalDate.parse} 抛异常。
     */
    private LocalDate parseDateLeniently(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "预计成交时间格式不正确，应为 yyyy-MM-dd，实际：" + text);
        }
    }

    // ==================== 内部方法：跨表补值 ====================

    /** 分页结果转 VO，并跨表补客户名 / 负责人姓名 / 创建人姓名 + 关注态 + 明细与统计 */
    private PageResult<OpportunityRespVO> toPageResult(Page<OpportunityDO> page) {
        List<OpportunityDO> records = page.getRecords();
        List<Long> ids = records.stream().map(OpportunityDO::getOpportunityId).toList();
        Map<Long, List<OpportunityItemDO>> itemsByOpportunity = loadItems(ids);
        List<OpportunityItemDO> allItems = itemsByOpportunity.values().stream()
                .flatMap(List::stream)
                .toList();

        Map<Long, String> customerNames = customerNameMap(records);
        Map<Long, String> ownerNames = userNameMap(records.stream()
                .map(OpportunityDO::getOwnerUserId).toList());
        Map<Long, String> creatorNames = userNameMap(records.stream()
                .map(OpportunityDO::getCreateBy).toList());
        // 关注态批量查一次，避免逐行查库；空页时 starredBizIds 返回空集
        Set<Long> starredIds = starService.starredBizIds(StarBizType.OPPORTUNITY, ids);
        // 单位名一次查完（「商品种类/总数」不需要它，但明细要回显）
        Map<Long, String> unitNames = itemSupport.loadUnitNames(unitIdsOf(allItems));

        List<OpportunityRespVO> list = records.stream().map(opportunity -> {
            OpportunityRespVO vo = opportunityConvert.toRespVO(opportunity);
            vo.setCustomerName(customerNames.get(opportunity.getCustomerId()));
            vo.setOwnerName(ownerNames.get(opportunity.getOwnerUserId()));
            vo.setCreator(creatorNames.get(opportunity.getCreateBy()));
            vo.setStarred(starredIds.contains(opportunity.getOpportunityId()));
            List<OpportunityItemDO> items = itemsByOpportunity
                    .getOrDefault(opportunity.getOpportunityId(), Collections.emptyList());
            fillItems(vo, items, unitNames);
            applyDerivedFields(vo, items);
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    /**
     * 填充「派生 / 仓库里没有对应列」的响应字段。
     *
     * <p>{@code discount} 已是真列（{@code discount_rate}），由 Convert 直接映射过来，这里只做
     * **NULL 归一**；{@code finalAmount} 与 {@code productKinds} / {@code productCount} 则没有列，
     * 集中在这一处赋值（加上 {@link OpportunityConvert} 里对应的 {@code ignore}），
     * 以后补列时只改这两个地方。字段清单与取舍见
     * {@code docs/09-商机模块字段契约.md} 的「待建表清单」。
     *
     * @param items 该商机的产品明细（已按 sort 升序）；统计口径见
     *              {@link #productKindsOf} 与 {@link #productCountOf}
     */
    private void applyDerivedFields(OpportunityRespVO vo, List<OpportunityItemDO> items) {
        // 整单折扣：真库这一列是可空的（NULL DEFAULT NULL），「不打折」应当显示成 0% 而不是 --，
        // 所以读路径把 NULL 归一成 0（写路径也永不写 NULL，见 create）
        BigDecimal discount = vo.getDiscount() == null ? BigDecimal.ZERO : vo.getDiscount();
        vo.setDiscount(discount);

        // 整单最终价 = 合计总价 ×(1 − 折扣/100)，与前端 form-config.js 的 compute 同一公式。
        // ⚠️ 注意：totalAmount（合计总价）本身不打折，只有这个字段是折后价；
        //    明细也**不改** totalAmount（前端那一格是用户手填的，见 docs/09 第七节 #7）
        BigDecimal totalAmount = vo.getTotalAmount() == null ? BigDecimal.ZERO : vo.getTotalAmount();
        BigDecimal rate = BigDecimal.ONE.subtract(discount.movePointLeft(2));
        vo.setFinalAmount(totalAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP));

        vo.setProductKinds(productKindsOf(items));
        vo.setProductCount(productCountOf(items));
        // nextContactTime 无列也无来源（crm_follow_up 未接），保持 null；
        // 全局 default-property-inclusion=non_null 会让它从 JSON 里消失，前端 dash() 兜底成 --
    }

    /**
     * 商品种类 = 明细里**不同产品**的个数。
     *
     * <p>有 {@code productId} 的按它去重；**没有 {@code productId} 的手填行各算一种**
     * （按 {@code itemId} 区分）—— 否则几行手填行会被并成 1 种，数字明显偏小。
     */
    private Integer productKindsOf(List<OpportunityItemDO> items) {
        long withProduct = items.stream()
                .map(OpportunityItemDO::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long anonymous = items.stream()
                .filter(item -> item.getProductId() == null)
                .count();
        return (int) (withProduct + anonymous);
    }

    /** 商品总数 = 明细数量合计（可能有小数，如 2.5 米），去掉无意义的尾零再给前端 */
    private BigDecimal productCountOf(List<OpportunityItemDO> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (OpportunityItemDO item : items) {
            if (item.getQuantity() != null) {
                total = total.add(item.getQuantity());
            }
        }
        return MoneyCalculator.stripZeros(total);
    }

    /**
     * 明细合计（<b>含税</b>）= Σ 行的 {@code crm_opportunity_item.amount}。
     *
     * <p><b>读路径不重算</b>：行金额在写入时已由 {@code ItemSupport} 按 3.6
     * （先逐行四舍五入）算好落库，这里直接加总，保证「合计总价」与明细表里的行金额**出自同一组数字**
     * —— 与报价单的 {@code profitOf} 用库里 {@code amount} 拆税是同一取舍。
     *
     * @return 明细为空时返回 {@code null}，表示「没有明细」→ 主表走手填预估，而不是被算成 0
     */
    private BigDecimal itemsAmountOf(List<OpportunityItemDO> items) {
        if (items.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (OpportunityItemDO item : items) {
            if (item.getAmount() != null) {
                total = total.add(item.getAmount());
            }
        }
        return MoneyCalculator.round2(total);
    }

    /**
     * 明细 → 主表「合计总价」回写（<b>本模块「明细生效」的唯一落点</b>）。
     *
     * <p>规则：<b>有明细就写，没有明细就不碰</b>。
     * <ul>
     *     <li>明细为空：记一条 {@code debug} 就返回 —— 明细清空后退回「手填预估」模式，
     *       静默把用户手上的金额抹成 0 比留着旧值危险（取舍见类注释）</li>
     *     <li>算出来的值与库里一致：跳过 UPDATE（省一次写，也让「明细没动则金额不变」可断言）</li>
     *     <li>只写 {@code amount} 一列：折扣、阶段、负责人一概不动</li>
     * </ul>
     *
     * @param opportunity 已经查过的商机（调用方在方法开头都用 {@code getOpportunityDO} 做过
     *                    存在性 / 越权校验，这里复用，避免多一次主键查询）
     * @param items       回写依据的**全量**明细（POST 是「已有 + 新增」，PUT 是覆盖后的新行）
     * @param scene       场景名，只用于日志（「追加产品明细」/「覆盖产品明细」）
     */
    private void syncAmountFromItems(OpportunityDO opportunity, List<OpportunityItemDO> items, String scene) {
        BigDecimal itemsAmount = itemsAmountOf(items);
        if (itemsAmount == null) {
            log.debug("[商机] {}：明细为空，合计总价保持原值（退回手填预估模式）：id={}, amount={}",
                    scene, opportunity.getOpportunityId(), opportunity.getAmount());
            return;
        }
        validateAmount(itemsAmount);
        if (itemsAmount.compareTo(opportunity.getAmount() == null ? BigDecimal.ZERO : opportunity.getAmount()) == 0) {
            log.debug("[商机] {}：合计总价与明细汇总一致（{}），无需回写：id={}",
                    scene, itemsAmount, opportunity.getOpportunityId());
            return;
        }

        OpportunityDO update = new OpportunityDO();
        update.setOpportunityId(opportunity.getOpportunityId());
        update.setAmount(itemsAmount);
        // 审计字段（update_by / update_time）由 DefaultDBFieldHandler 自动填充。
        // 越权时拦截器会把 UPDATE 也改写掉（影响 0 行），而调用方已经先查过主单 → 不会走到这里
        opportunityMapper.updateById(update);
        log.info("[商机] {}：合计总价按明细汇总回写：id={}, {} → {}, 明细{}行",
                scene, opportunity.getOpportunityId(), opportunity.getAmount(), itemsAmount, items.size());
    }

    /**
     * 「合计总价」容量校验（{@link #AMOUNT_MAX}）。
     *
     * <p>为什么放在这里而不是只靠接口层校验：这个值是**算出来的**，而 {@code @Digits} 只校验
     * 客户端提交的值 —— 单价与数量各自合法，乘积仍可能超出列的容量。不拦的话 MySQL 报错 → 500，
     * 用户看到的是「服务器异常」而不是「金额超上限」。
     */
    private void validateAmount(BigDecimal amount) {
        if (amount.compareTo(AMOUNT_MAX) > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "产品明细合计超出「合计总价」上限（" + AMOUNT_MAX.toPlainString() + "），请检查数量与单价");
        }
    }

    /** 批量取客户名，避免 N+1 */
    private Map<Long, String> customerNameMap(List<OpportunityDO> records) {
        List<Long> customerIds = records.stream()
                .map(OpportunityDO::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (customerIds.isEmpty()) {
            // ⚠️ 必须用 Collections.emptyMap() 而不是 Map.of()：Map.of() 返回的
            //    ImmutableCollections 的 get(null) 会抛 NPE，而 get(customerId) 一定可能传入 null
            return Collections.emptyMap();
        }
        return customerMiniMapper.selectList(CustomerMiniDO::getCustomerId, customerIds).stream()
                .filter(customer -> customer.getCustomerName() != null)
                .collect(Collectors.toMap(CustomerMiniDO::getCustomerId,
                        CustomerMiniDO::getCustomerName, (a, b) -> a));
    }

    /**
     * 批量取用户姓名，避免 N+1（负责人、创建人、阶段变更的操作人共用）。
     *
     * <p>{@code Collectors.toMap} 不接受 null 值，所以先过滤掉没有姓名的用户。
     */
    private Map<Long, String> userNameMap(Collection<Long> userIds) {
        List<Long> distinctIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            // 同 customerNameMap：空 Map 用 Map.of() 的话，get(null) 会让整个接口 500
            return Collections.emptyMap();
        }
        return sysUserMiniMapper.selectList(SysUserMiniDO::getUserId, distinctIds).stream()
                .filter(user -> user.getRealName() != null)
                .collect(Collectors.toMap(SysUserMiniDO::getUserId, SysUserMiniDO::getRealName, (a, b) -> a));
    }

    private String loadCustomerName(Long customerId) {
        if (customerId == null) {
            return null;
        }
        CustomerMiniDO customer = customerMiniMapper.selectById(customerId);
        return customer == null ? null : customer.getCustomerName();
    }

    private String loadUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUserMiniDO user = sysUserMiniMapper.selectById(userId);
        return user == null ? null : user.getRealName();
    }

    // ==================== 内部方法：校验 ====================

    private OpportunityDO getOpportunityDO(Long id) {
        OpportunityDO opportunityDO = opportunityMapper.selectById(id);
        if (opportunityDO == null) {
            throw new ServiceException(ErrorCode.OPPORTUNITY_NOT_EXISTS);
        }
        return opportunityDO;
    }

    /** 逻辑外键校验：客户必须存在（建表文档 1.6，应用层负责引用完整性） */
    private void requireCustomerExists(Long customerId) {
        if (customerId == null) {
            return;
        }
        if (customerMiniMapper.selectById(customerId) == null) {
            throw new ServiceException(ErrorCode.CUSTOMER_NOT_EXISTS);
        }
    }

    /**
     * 逻辑外键校验：负责人必须是真实存在的用户，并返回该用户（用于取部门）。
     *
     * <p>返回 {@code null} 只在 {@code userId} 为 null 时发生 ——
     * 上游已经兜底成当前登录人，所以实践中不会走到。
     */
    private SysUserMiniDO requireUserExists(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUserMiniDO user = sysUserMiniMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException(ErrorCode.USER_NOT_EXISTS, "负责人不存在：" + userId);
        }
        return user;
    }

    /** 过滤条件命中「不可能有结果」的场景（如仅本人范围下查下属）时的空分页结果 */
    private <T> PageResult<T> emptyResult(PageParam reqVO) {
        Page<?> page = reqVO.toPage();
        return PageResult.empty((int) page.getCurrent(), (int) page.getSize());
    }

    /**
     * 校验整单折扣（0–100），{@code null} 表示「不传」，直接放行。
     *
     * <p>范围校验在 Controller 的 {@code @Valid}（{@code @DecimalMin/@DecimalMax}）上已经有了一次，
     * 这里再做一次是因为**折扣会直接缩放一个金额**
     * （{@code finalAmount = amount × (1 − discount/100)}），超出范围会算出负数；
     * 而 Service 也可能被别的模块直接调用（那就不经过 {@code @Valid}）。
     */
    private void validateDiscount(BigDecimal discount) {
        if (discount == null) {
            return;
        }
        if (discount.compareTo(BigDecimal.ZERO) < 0 || discount.compareTo(HUNDRED) > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "整单折扣必须在 0–100 之间，实际：" + discount);
        }
    }
}
