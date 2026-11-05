package com.aurum.crm.module.crm.service.impl;

import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.core.SuperPermission;
import com.aurum.crm.module.crm.mapper.ContractMapper;
import com.aurum.crm.module.crm.mapper.CustomerMapper;
import com.aurum.crm.module.crm.mapper.FinInvoiceMiniMapper;
import com.aurum.crm.module.crm.mapper.FinPaymentMiniMapper;
import com.aurum.crm.module.crm.mapper.LeadMapper;
import com.aurum.crm.module.crm.mapper.OpportunityMapper;
import com.aurum.crm.module.crm.mapper.SysApprovalInstanceMapper;
import com.aurum.crm.module.crm.mapper.SysApprovalTaskMapper;
import com.aurum.crm.module.crm.mapper.SysNoticeMiniMapper;
import com.aurum.crm.module.crm.model.ContractDO;
import com.aurum.crm.module.crm.model.CustomerDO;
import com.aurum.crm.module.crm.model.FinInvoiceMiniDO;
import com.aurum.crm.module.crm.model.FinPaymentMiniDO;
import com.aurum.crm.module.crm.model.LeadDO;
import com.aurum.crm.module.crm.model.OpportunityDO;
import com.aurum.crm.module.crm.model.SysApprovalInstanceDO;
import com.aurum.crm.module.crm.model.SysApprovalTaskDO;
import com.aurum.crm.module.crm.model.SysNoticeMiniDO;
import com.aurum.crm.module.crm.model.dto.ContractPageReqVO;
import com.aurum.crm.module.crm.model.dto.CustomerPageReqVO;
import com.aurum.crm.module.crm.model.dto.DashboardFocusReqVO;
import com.aurum.crm.module.crm.model.dto.DashboardMessageReqVO;
import com.aurum.crm.module.crm.model.dto.LeadPageReqDTO;
import com.aurum.crm.module.crm.model.dto.OpportunityPageReqVO;
import com.aurum.crm.module.crm.model.dto.QuotationPageReqVO;
import com.aurum.crm.module.crm.model.vo.DashboardMessageRespVO;
import com.aurum.crm.module.crm.model.vo.DashboardShortcutRespVO;
import com.aurum.crm.module.crm.model.vo.DashboardStatsRespVO;
import com.aurum.crm.module.crm.model.vo.DashboardTodoRespVO;
import com.aurum.crm.module.crm.model.vo.DashboardTrendPointRespVO;
import com.aurum.crm.module.crm.model.vo.OpportunityFunnelRespVO;
import com.aurum.crm.module.crm.service.ContractService;
import com.aurum.crm.module.crm.service.CustomerService;
import com.aurum.crm.module.crm.service.DashboardService;
import com.aurum.crm.module.crm.service.LeadService;
import com.aurum.crm.module.crm.service.OpportunityService;
import com.aurum.crm.module.crm.service.QuotationService;
import com.aurum.crm.module.system.dal.dataobject.SysMenuDO;
import com.aurum.crm.module.system.dal.mysql.SysMenuMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 首页看板实现。
 *
 * <h3>数据权限是白拿的</h3>
 *
 * <p>{@code crm_lead} / {@code crm_customer} / {@code crm_opportunity} / {@code crm_contract} /
 * {@code fin_payment} / {@code fin_invoice} 都在 {@code DataPermissionInnerInterceptor}
 * 的 {@code SCOPED_TABLES} 白名单里，所以**下面所有基于这些表的查询都会被自动追加
 * 行级过滤条件** —— 不需要在这里写一行权限代码。
 *
 * <p>⚠️ 但 {@code sys_notice} / {@code sys_approval_task} **不在白名单**，
 * 它们的可见性由「接收人 / 审批人」决定，所以下面**显式**带了
 * {@code receiver_id = 当前登录人} / {@code approver_id = 当前登录人}。
 * 漏了这两个条件会把别人的消息读出来。
 *
 * <h3>为什么有些统计在应用层聚合</h3>
 *
 * <p>趋势图需要按「天 / 小时」分组，而 {@code ContractMapper} 等是**共享 Mapper**，
 * 往里加 {@code @Select} 聚合方法会和别的改动打架。看板是低频查询、区间最多一个月，
 * 所以这里直接取回区间内的行在应用层聚合。
 * <b>数据量增长后应该改成数据库聚合</b>（两个财务 mini Mapper 就是那么做的 ——
 * 它们是看板自己新加的文件，加方法没有冲突）。
 */
@Slf4j
@Service
public class DashboardServiceImpl implements DashboardService {

    /** 趋势图上「一天」的粒度：24 个整点 */
    private static final int HOURS_PER_DAY = 24;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    /** 消息时间显示用；公告标题为空时的兜底文案 */
    private static final String UNTITLED = "（无标题）";

    /**
     * 快捷入口的图标底色。
     *
     * <p>{@code sys_menu} 没有颜色列，所以按入口的**排序位置**从这个调色板取 ——
     * 用位置而不是随机，保证同一份菜单每次返回的配色稳定（刷新一次换一个颜色会很难看）。
     * 取模循环，入口多于颜色数时从头再来。
     */
    private static final String[] ICON_COLORS = {
            "#2C6DDB", "#28D094", "#F59A23", "#666EE8", "#17B0C4",
            "#E8657A", "#8E6FE8", "#3DA35D", "#D9822B", "#4A90D9"
    };

    /** 金额可见的角色标识：财务与超管。其余角色三个金额字段返回 null */
    private static final Set<String> AMOUNT_ROLES = Set.of("admin", "finance");

    @Resource private LeadMapper leadMapper;
    @Resource private CustomerMapper customerMapper;
    @Resource private OpportunityMapper opportunityMapper;
    @Resource private ContractMapper contractMapper;
    @Resource private FinPaymentMiniMapper finPaymentMiniMapper;
    @Resource private FinInvoiceMiniMapper finInvoiceMiniMapper;
    @Resource private SysNoticeMiniMapper sysNoticeMiniMapper;
    @Resource private SysApprovalTaskMapper sysApprovalTaskMapper;
    @Resource private SysApprovalInstanceMapper sysApprovalInstanceMapper;
    @Resource private SysMenuMapper sysMenuMapper;

    @Resource private LeadService leadService;
    @Resource private CustomerService customerService;
    @Resource private OpportunityService opportunityService;
    @Resource private QuotationService quotationService;
    @Resource private ContractService contractService;

    // ========================================================================
    //  9.1 待办事项
    // ========================================================================

    @Override
    public DashboardTodoRespVO todo() {
        DashboardTodoRespVO vo = new DashboardTodoRespVO();

        // 待回访客户：填了下次联系时间、已经过期、且还没成交
        vo.setPendingVisitCustomer(customerMapper.selectCount(
                Wrappers.<CustomerDO>lambdaQuery()
                        .isNotNull(CustomerDO::getNextContactTime)
                        .lt(CustomerDO::getNextContactTime, LocalDate.now())
                        .eq(CustomerDO::getDealStatus, DEAL_STATUS_UNDEALT)));

        // 待处理线索：还没转化
        vo.setPendingLead(leadMapper.selectCount(
                Wrappers.<LeadDO>lambdaQuery().isNull(LeadDO::getConvertTime)));

        // 待处理商机：既没赢单也没输单
        vo.setPendingOpportunity(opportunityMapper.selectCount(
                Wrappers.<OpportunityDO>lambdaQuery()
                        .notIn(OpportunityDO::getStage, STAGE_WON, STAGE_LOST)));

        // 待审核合同：审批中
        vo.setPendingContractAudit(contractMapper.selectCount(
                Wrappers.<ContractDO>lambdaQuery().eq(ContractDO::getAuditStatus, AUDIT_AUDITING)));

        // 待财务审核：回款单审批中
        vo.setPendingFinanceAudit(finPaymentMiniMapper.selectCount(
                Wrappers.<FinPaymentMiniDO>lambdaQuery()
                        .eq(FinPaymentMiniDO::getDelFlag, NOT_DELETED)
                        .eq(FinPaymentMiniDO::getAuditStatus, AUDIT_AUDITING)));

        // 待财务开票：发票状态「待开票」
        vo.setPendingInvoice(finInvoiceMiniMapper.selectCount(
                Wrappers.<FinInvoiceMiniDO>lambdaQuery()
                        .eq(FinInvoiceMiniDO::getDelFlag, NOT_DELETED)
                        .eq(FinInvoiceMiniDO::getStatus, INVOICE_PENDING)));

        return vo;
    }

    // ========================================================================
    //  9.2 实时数据
    // ========================================================================

    @Override
    public DashboardStatsRespVO stats(String range) {
        LocalDateTime start = rangeStart(range);
        boolean canViewAmount = canViewAmount();

        DashboardStatsRespVO vo = new DashboardStatsRespVO();
        vo.setLeadCount(leadMapper.selectCount(
                Wrappers.<LeadDO>lambdaQuery().ge(LeadDO::getCreateTime, start)));
        vo.setCustomerCount(customerMapper.selectCount(
                Wrappers.<CustomerDO>lambdaQuery().ge(CustomerDO::getCreateTime, start)));
        vo.setOpportunityCount(opportunityMapper.selectCount(
                Wrappers.<OpportunityDO>lambdaQuery().ge(OpportunityDO::getCreateTime, start)));
        vo.setContractCount(contractMapper.selectCount(
                Wrappers.<ContractDO>lambdaQuery().ge(ContractDO::getCreateTime, start)));

        // 金额三项对非财务角色置 null（字段级权限，见《05-接口文档》1.7）
        if (canViewAmount) {
            vo.setSalesAmount(sumContractAmount(contractMapper.selectList(
                    Wrappers.<ContractDO>lambdaQuery()
                            .select(ContractDO::getAmount)
                            .ge(ContractDO::getCreateTime, start))));
            vo.setReceivedAmount(finPaymentMiniMapper.sumApprovedAmount(start));
            vo.setInvoicedAmount(finInvoiceMiniMapper.sumIssuedAmount(start));
        }
        return vo;
    }

    // ========================================================================
    //  9.3 我关注的
    // ========================================================================

    @Override
    public PageResult<Object> focus(DashboardFocusReqVO reqVO) {
        String tab = reqVO.getTab() == null ? "" : reqVO.getTab().trim();
        String scope = reqVO.getScope();

        // 复用各模块自己的分页查询，返回它们自己的 RespVO ——
        // 字段口径（码值 / id 别名 / region 派生字段 / ownerName 补名）全都现成，
        // 不在这里重新拼一遍，否则两边迟早漂移。
        switch (tab) {
            case "lead" -> {
                LeadPageReqDTO req = new LeadPageReqDTO();
                copyPage(reqVO, req);
                req.setScope(scope);
                return cast(leadService.pageQuery(req));
            }
            case "customer" -> {
                CustomerPageReqVO req = new CustomerPageReqVO();
                copyPage(reqVO, req);
                req.setScope(scope);
                return cast(customerService.pageQuery(req));
            }
            case "opportunity" -> {
                OpportunityPageReqVO req = new OpportunityPageReqVO();
                copyPage(reqVO, req);
                req.setScope(scope);
                return cast(opportunityService.pageQuery(req));
            }
            case "quotation" -> {
                QuotationPageReqVO req = new QuotationPageReqVO();
                copyPage(reqVO, req);
                req.setScope(scope);
                return cast(quotationService.pageQuery(req));
            }
            case "contract" -> {
                ContractPageReqVO req = new ContractPageReqVO();
                copyPage(reqVO, req);
                req.setScope(scope);
                return cast(contractService.pageQuery(req));
            }
            // 回款 / 发票：**委托尚未接上**，返回空页。
            //
            // ⚠️ 这里原先写的理由是「财务模块目前是空的，没有可委托的 Service」——
            //    **该说法已过时**（2026-09-14 核对）：`crm-module-finance` 已有
            //    `PaymentService` / `InvoiceService` 与各自的列表接口
            //    （`GET /finance/payment`、`GET /finance/invoice`）。
            //    所以**不是「做不了」，只是「还没接」**。
            //    要接通只需照着上面 contract 那个 case 的写法委托过去，
            //    不用先"把财务模块做出来" —— 这一点很容易被旧注释误导。
            //
            // 为什么是返回空页而不是「退化成不过滤」：后者会让人以为筛选生效了，
            // 看到的是未经收窄的数据（项目里已踩过这个坑，见 OpportunityServiceImpl 的注释）。
            case "payment", "invoice" -> {
                log.debug("[看板] focus tab={} 尚未接上财务模块的委托，返回空页", tab);
                return PageResult.empty(safePageNo(reqVO), safePageSize(reqVO));
            }
            default -> {
                log.warn("[看板] focus 收到未支持的 tab：{}，返回空页", tab);
                return PageResult.empty(safePageNo(reqVO), safePageSize(reqVO));
            }
        }
    }

    // ========================================================================
    //  9.4 消息 / 9.5 公告 | 通知
    // ========================================================================

    @Override
    public PageResult<DashboardMessageRespVO> messages(DashboardMessageReqVO reqVO) {
        Long me = LoginUserHolder.getRequired().getUserId();
        String type = trimToNull(reqVO.getType());

        List<DashboardMessageRespVO> all = new ArrayList<>();
        // type 为空 = 全部三类；指定了就只取那一类
        if (type == null || TYPE_APPROVAL.equals(type)) {
            all.addAll(approvalMessages(me));
        }
        if (type == null || TYPE_NOTICE.equals(type) || TYPE_ANNOUNCEMENT.equals(type)) {
            all.addAll(noticeMessages(me, type));
        }
        return paginateInMemory(all, reqVO);
    }

    @Override
    public PageResult<DashboardMessageRespVO> notices(DashboardMessageReqVO reqVO) {
        Long me = LoginUserHolder.getRequired().getUserId();
        String type = trimToNull(reqVO.getType());
        // 公告|通知区块 = sys_notice 里除「审批提醒」之外的全部（通知 + 公海回收 + 公告）
        return paginateInMemory(noticeMessages(me, type), reqVO);
    }

    /**
     * 待办审批 → 消息行。
     *
     * <p>数据源是 {@code sys_approval_task}：{@code approver_id = 我} 且
     * {@code task_status = 0（待办）}。这条查询有 {@code idx_todo} 索引专门支撑。
     */
    private List<DashboardMessageRespVO> approvalMessages(Long me) {
        List<SysApprovalTaskDO> tasks = sysApprovalTaskMapper.selectList(
                Wrappers.<SysApprovalTaskDO>lambdaQuery()
                        .eq(SysApprovalTaskDO::getApproverId, me)
                        .eq(SysApprovalTaskDO::getTaskStatus, TASK_PENDING)
                        .orderByDesc(SysApprovalTaskDO::getCreateTime));
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }

        // 实例信息批量查一次，避免逐条查库
        List<Long> instanceIds = tasks.stream()
                .map(SysApprovalTaskDO::getInstanceId).filter(Objects::nonNull).distinct().toList();
        Map<Long, SysApprovalInstanceDO> instances = instanceIds.isEmpty()
                ? Collections.emptyMap()
                : sysApprovalInstanceMapper.selectList(SysApprovalInstanceDO::getInstanceId, instanceIds)
                        .stream()
                        .collect(Collectors.toMap(SysApprovalInstanceDO::getInstanceId, Function.identity(),
                                (a, b) -> a));

        return tasks.stream().map(task -> {
            SysApprovalInstanceDO instance = instances.get(task.getInstanceId());
            String title = instance == null || !StringUtils.hasText(instance.getTitle())
                    ? UNTITLED
                    : instance.getTitle();
            return new DashboardMessageRespVO(
                    task.getTaskId(),
                    TYPE_APPROVAL,
                    "审批",
                    title,
                    approvalLink(instance),
                    format(task.getCreateTime()));
        }).toList();
    }

    /**
     * {@code sys_notice} → 消息行。
     *
     * <p>⚠️ {@code receiver_id} 条件**必须带**：{@code sys_notice} 不在数据权限白名单里，
     * 漏了就会读出别人的消息。
     *
     * @param onlyType 指定时只取该类型；为 null 时取「除审批提醒外」的全部
     */
    private List<DashboardMessageRespVO> noticeMessages(Long me, String onlyType) {
        LambdaQueryWrapper<SysNoticeMiniDO> wrapper = Wrappers.<SysNoticeMiniDO>lambdaQuery()
                .eq(SysNoticeMiniDO::getReceiverId, me)          // ← 不可省略
                .eq(SysNoticeMiniDO::getDelFlag, NOT_DELETED)
                .orderByDesc(SysNoticeMiniDO::getSendTime);

        if (TYPE_ANNOUNCEMENT.equals(onlyType)) {
            wrapper.eq(SysNoticeMiniDO::getNoticeType, NOTICE_TYPE_ANNOUNCEMENT);
        } else if (TYPE_NOTICE.equals(onlyType)) {
            wrapper.in(SysNoticeMiniDO::getNoticeType, NOTICE_TYPE_NOTICE_SET);
        } else {
            // 未指定（或 messages 里的通知/公告两类）：除「审批提醒」外全部
            wrapper.ne(SysNoticeMiniDO::getNoticeType, NOTICE_TYPE_APPROVAL);
        }

        return sysNoticeMiniMapper.selectList(wrapper).stream()
                .map(n -> new DashboardMessageRespVO(
                        n.getNoticeId(),
                        noticeTypeKey(n.getNoticeType()),
                        noticeTypeName(n.getNoticeType()),
                        StringUtils.hasText(n.getTitle()) ? n.getTitle() : UNTITLED,
                        null,
                        format(n.getSendTime())))
                .toList();
    }

    // ========================================================================
    //  9.6 快捷入口
    // ========================================================================

    @Override
    public List<DashboardShortcutRespVO> shortcuts() {
        LoginUser me = LoginUserHolder.getRequired();
        Set<String> permissions = me.getPermissions();
        // 超管的权限集合里只有合成的 *:*:*，不做逐项判断。
        // 注意这里是【字面量 contains】而不是交给 Sa-Token 去通配匹配 ——
        // 后者会把 *:*:* 当普通通配模式、匹配不了两段式权限码（BUG-003），
        // 那种坑只在 @SaCheckPermission 那条路上。这处本来就是对的。
        boolean superAdmin = permissions != null && permissions.contains(SuperPermission.WILDCARD);

        List<SysMenuDO> menus = sysMenuMapper.selectList(Wrappers.<SysMenuDO>lambdaQuery()
                .eq(SysMenuDO::getMenuType, MENU_TYPE_MENU)
                .eq(SysMenuDO::getVisible, VISIBLE)
                .eq(SysMenuDO::getStatus, STATUS_ENABLED)
                .isNotNull(SysMenuDO::getPath)
                .orderByAsc(SysMenuDO::getSort)
                .orderByAsc(SysMenuDO::getMenuId));

        List<DashboardShortcutRespVO> list = new ArrayList<>();
        for (SysMenuDO menu : menus) {
            // 没配权限点的菜单（如首页自身）对所有登录人可见
            if (!superAdmin && StringUtils.hasText(menu.getPerms())
                    && (permissions == null || !permissions.contains(menu.getPerms()))) {
                continue;
            }
            list.add(new DashboardShortcutRespVO(
                    menu.getMenuName(),
                    menu.getIcon(),
                    ICON_COLORS[list.size() % ICON_COLORS.length],
                    menu.getPath()));
        }
        return list;
    }

    // ========================================================================
    //  9.7 漏斗（委托商机模块）
    // ========================================================================

    @Override
    public List<OpportunityFunnelRespVO> funnel() {
        return opportunityService.funnel();
    }

    // ========================================================================
    //  9.8 业绩趋势
    // ========================================================================

    @Override
    public List<DashboardTrendPointRespVO> trend(String range) {
        LocalDateTime start = rangeStart(range);
        boolean canViewAmount = canViewAmount();

        // 取回区间内的行，在应用层按「天 / 小时」分桶。
        // 为什么不用 SQL 的 GROUP BY：这几个 Mapper 是共享文件，加聚合方法容易和别的改动打架；
        // 看板是低频查询、区间最多一个月，应用层聚合完全够用（见类注释）。
        List<LeadDO> leads = leadMapper.selectList(
                Wrappers.<LeadDO>lambdaQuery().select(LeadDO::getCreateTime)
                        .ge(LeadDO::getCreateTime, start));
        List<CustomerDO> customers = customerMapper.selectList(
                Wrappers.<CustomerDO>lambdaQuery().select(CustomerDO::getCreateTime)
                        .ge(CustomerDO::getCreateTime, start));
        List<ContractDO> contracts = contractMapper.selectList(
                Wrappers.<ContractDO>lambdaQuery()
                        .select(ContractDO::getCreateTime, ContractDO::getAmount)
                        .ge(ContractDO::getCreateTime, start));

        // 桶的边界由 range 决定：day 按小时，week/month 按天
        boolean hourly = RANGE_DAY.equals(normalizeRange(range));
        List<LocalDateTime> buckets = buildBuckets(range);

        Map<LocalDateTime, Long> leadMap = countByBucket(leads, LeadDO::getCreateTime, hourly);
        Map<LocalDateTime, Long> customerMap = countByBucket(customers, CustomerDO::getCreateTime, hourly);
        Map<LocalDateTime, Long> contractMap = countByBucket(contracts, ContractDO::getCreateTime, hourly);
        Map<LocalDateTime, BigDecimal> amountMap = canViewAmount
                ? sumByBucket(contracts, hourly)
                : Collections.emptyMap();

        DateTimeFormatter labelFmt = hourly ? HOUR_LABEL : DAY_LABEL;
        return buckets.stream().map(bucket -> new DashboardTrendPointRespVO(
                bucket.format(labelFmt),
                leadMap.getOrDefault(bucket, 0L),
                customerMap.getOrDefault(bucket, 0L),
                contractMap.getOrDefault(bucket, 0L),
                canViewAmount ? amountMap.getOrDefault(bucket, BigDecimal.ZERO) : null
        )).toList();
    }

    // ========================================================================
    //  内部方法：时间区间
    // ========================================================================

    /** 统计区间的起点（含）。未识别的取值按「本月」处理，不报错 */
    private LocalDateTime rangeStart(String range) {
        LocalDate today = LocalDate.now();
        return switch (normalizeRange(range)) {
            case RANGE_DAY -> today.atStartOfDay();
            case RANGE_WEEK -> today.with(DayOfWeek.MONDAY).atStartOfDay();
            default -> today.withDayOfMonth(1).atStartOfDay();
        };
    }

    private String normalizeRange(String range) {
        return switch (range == null ? "" : range.trim()) {
            case RANGE_DAY -> RANGE_DAY;
            case RANGE_WEEK -> RANGE_WEEK;
            default -> RANGE_MONTH;
        };
    }

    /**
     * 趋势图的横轴桶。
     *
     * <ul>
     *     <li>{@code day} —— 今天的 24 个整点</li>
     *     <li>{@code week} —— 本周一 ~ 周日（7 个整天，含未来的天数，这样横轴长度稳定）</li>
     *     <li>{@code month} —— 本月 1 号 ~ 今天（不含未来，避免尾巴上一串 0）</li>
     * </ul>
     *
     * <p><b>补齐没有数据的桶是必须的</b>：折线图按数组下标连线，
     * 缺桶会让相邻两点直接连起来，看起来像那天有数据。所以这里先造出完整的桶列表，
     * 再把聚合结果填进去（缺的填 0）。
     */
    private List<LocalDateTime> buildBuckets(String range) {
        LocalDate today = LocalDate.now();
        List<LocalDateTime> buckets = new ArrayList<>();
        switch (normalizeRange(range)) {
            case RANGE_DAY -> {
                LocalDateTime hour = today.atStartOfDay();
                for (int i = 0; i < HOURS_PER_DAY; i++) {
                    buckets.add(hour.plusHours(i));
                }
            }
            case RANGE_WEEK -> {
                LocalDateTime monday = today.with(DayOfWeek.MONDAY).atStartOfDay();
                for (int i = 0; i < DAYS_PER_WEEK; i++) {
                    buckets.add(monday.plusDays(i));
                }
            }
            default -> {
                LocalDateTime first = today.withDayOfMonth(1).atStartOfDay();
                long days = ChronoUnit.DAYS.between(today.withDayOfMonth(1), today) + 1;
                for (int i = 0; i < days; i++) {
                    buckets.add(first.plusDays(i));
                }
            }
        }
        return buckets;
    }

    /** 把时间截断到所属的桶 */
    private LocalDateTime bucketOf(LocalDateTime time, boolean hourly) {
        return hourly ? time.truncatedTo(ChronoUnit.HOURS) : time.toLocalDate().atStartOfDay();
    }

    private <T> Map<LocalDateTime, Long> countByBucket(List<T> rows,
                                                       Function<T, LocalDateTime> timeGetter,
                                                       boolean hourly) {
        return rows.stream()
                .map(timeGetter)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(t -> bucketOf(t, hourly), Collectors.counting()));
    }

    private Map<LocalDateTime, BigDecimal> sumByBucket(List<ContractDO> contracts, boolean hourly) {
        return contracts.stream()
                .filter(c -> c.getCreateTime() != null)
                .collect(Collectors.groupingBy(
                        c -> bucketOf(c.getCreateTime(), hourly),
                        Collectors.reducing(BigDecimal.ZERO,
                                c -> c.getAmount() == null ? BigDecimal.ZERO : c.getAmount(),
                                BigDecimal::add)));
    }

    // ========================================================================
    //  内部方法：字段级权限
    // ========================================================================

    /**
     * 当前登录人是否能看到金额。
     *
     * <p>《05-接口文档》9.2：非财务角色 {@code salesAmount} / {@code receivedAmount} /
     * {@code invoicedAmount} 返回 {@code null}。
     *
     * <p>项目里**没有**金额相关的权限点（{@code sys_menu} 只有一个 {@code dashboard:view}），
     * 所以按角色标识判断：{@code admin} 或 {@code finance} 放行。
     */
    private boolean canViewAmount() {
        Set<String> roleKeys = LoginUserHolder.getRequired().getRoleKeys();
        if (roleKeys == null || roleKeys.isEmpty()) {
            // 取不到角色时**不放行** —— 宁可少显示，也不要越权暴露金额
            return false;
        }
        return roleKeys.stream().anyMatch(AMOUNT_ROLES::contains);
    }

    // ========================================================================
    //  内部方法：小工具
    // ========================================================================

    private BigDecimal sumContractAmount(List<ContractDO> contracts) {
        return contracts.stream()
                .map(c -> c.getAmount() == null ? BigDecimal.ZERO : c.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 审批行点击后的跳转目标，按业务类型映射到对应的审核页 */
    private String approvalLink(SysApprovalInstanceDO instance) {
        if (instance == null || instance.getBizType() == null) {
            return null;
        }
        return switch (instance.getBizType()) {
            case "contract" -> "/crm/contract-audit";
            case "payment" -> "/finance/payment-audit";
            case "invoice" -> "/finance/invoice-issue";
            default -> null;
        };
    }

    /** {@code notice_type} → 前端用的类型标识（决定标签配色） */
    private String noticeTypeKey(Integer noticeType) {
        return Objects.equals(noticeType, NOTICE_TYPE_ANNOUNCEMENT) ? TYPE_ANNOUNCEMENT : TYPE_NOTICE;
    }

    private String noticeTypeName(Integer noticeType) {
        if (noticeType == null) {
            return "通知";
        }
        return switch (noticeType) {
            case NOTICE_TYPE_ANNOUNCEMENT -> "公告";
            case NOTICE_TYPE_APPROVAL -> "审批";
            default -> "通知";
        };
    }

    private String format(LocalDateTime time) {
        return time == null ? "" : TIME_FMT.format(time);
    }

    /**
     * 两个数据源合并后的内存分页。
     *
     * <p>审批来自 {@code sys_approval_task}、通知公告来自 {@code sys_notice} —— 是两张表，
     * 没法用一次 SQL 分页。看板的这类数据量天然很小（一个人的待办 + 收到的通知），
     * 所以合并后在内存里排序、分页。
     */
    private PageResult<DashboardMessageRespVO> paginateInMemory(List<DashboardMessageRespVO> all,
                                                                DashboardMessageReqVO reqVO) {
        List<DashboardMessageRespVO> sorted = all.stream()
                .sorted(Comparator.comparing(DashboardMessageRespVO::getTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int pageNo = safePageNo(reqVO);
        int pageSize = safePageSize(reqVO);
        int from = Math.min((pageNo - 1) * pageSize, sorted.size());
        int to = Math.min(from + pageSize, sorted.size());
        return new PageResult<>(new ArrayList<>(sorted.subList(from, to)),
                (long) sorted.size(), pageNo, pageSize);
    }

    private int safePageNo(DashboardFocusReqVO reqVO) {
        Integer no = reqVO.getPageNo();
        return no == null || no < 1 ? 1 : no;
    }

    private int safePageSize(DashboardFocusReqVO reqVO) {
        Integer size = reqVO.getPageSize();
        return size == null || size < 1 ? 10 : size;
    }

    private int safePageNo(DashboardMessageReqVO reqVO) {
        Integer no = reqVO.getPageNo();
        return no == null || no < 1 ? 1 : no;
    }

    private int safePageSize(DashboardMessageReqVO reqVO) {
        Integer size = reqVO.getPageSize();
        return size == null || size < 1 ? 10 : size;
    }

    /** 分页参数从看板的统一入参复制到各模块自己的分页 DTO 上 */
    private void copyPage(DashboardFocusReqVO from, com.aurum.crm.mybatis.core.PageParam to) {
        to.setPageNo(safePageNo(from));
        to.setPageSize(safePageSize(from));
    }

    /**
     * 把「某个模块的 PageResult」转成「Object 的 PageResult」。
     *
     * <p>focus 要兼容 7 种单据类型，每种返回自己的 RespVO，所以 Service 的返回类型只能是
     * {@code PageResult<Object>}。这里的转换只换泛型，不动内容 ——
     * {@code PageResult} 的泛型信息在运行时本来就不存在（类型擦除）。
     */
    @SuppressWarnings("unchecked")
    private PageResult<Object> cast(PageResult<?> source) {
        return (PageResult<Object>) source;
    }

    private String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    // ========================================================================
    //  常量
    // ========================================================================

    private static final int NOT_DELETED = 0;

    /** 审核状态：1 审批中（0待提审 1审批中 2已通过 3已驳回） */
    private static final int AUDIT_AUDITING = 1;

    /** 发票单据状态：0 待开票（0待开票 1已开票 2已作废） */
    private static final int INVOICE_PENDING = 0;

    /** 客户成交状态：1 未成交（1未成交 2已成交） */
    private static final int DEAL_STATUS_UNDEALT = 1;

    /** 商机阶段：5 赢单 / 6 输单（两个终态） */
    private static final int STAGE_WON = 5;
    private static final int STAGE_LOST = 6;

    /** 审批任务状态：0 待办 */
    private static final int TASK_PENDING = 0;

    /** {@code sys_notice.notice_type}：1审批提醒 2公海回收提醒 3系统公告 4任务提醒 */
    private static final int NOTICE_TYPE_APPROVAL = 1;
    private static final int NOTICE_TYPE_ANNOUNCEMENT = 3;
    /** 「通知」类 = 公海回收提醒 + 任务提醒 */
    private static final List<Integer> NOTICE_TYPE_NOTICE_SET = List.of(2, 4);

    /** 消息对外的类型标识（前端按它决定标签配色） */
    private static final String TYPE_APPROVAL = "approval";
    private static final String TYPE_NOTICE = "notice";
    private static final String TYPE_ANNOUNCEMENT = "announcement";

    /** 统计维度 */
    private static final String RANGE_DAY = "day";
    private static final String RANGE_WEEK = "week";
    private static final String RANGE_MONTH = "month";
    private static final int DAYS_PER_WEEK = 7;

    /** {@code sys_menu} 取值：2 菜单 / 1 显示 / 1 正常 */
    private static final int MENU_TYPE_MENU = 2;
    private static final int VISIBLE = 1;
    private static final int STATUS_ENABLED = 1;

    // 超管的通配权限标识不在这里定义 —— 已上提到 SuperPermission.WILDCARD（crm-common），
    // 与 SaTokenConfig 的权限匹配、AuthServiceImpl 的合成共用同一份，避免三处各写一遍。
}
