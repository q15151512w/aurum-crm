package com.aurum.crm.module.crm.ai.tool.impl;

import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.crm.model.vo.DashboardStatsRespVO;
import com.aurum.crm.module.crm.model.vo.DashboardTodoRespVO;
import com.aurum.crm.module.crm.service.DashboardService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 首页看板概览：各业务对象数量 + 金额 + 待办。
 *
 * <p>走 {@code DashboardService}，所以**金额字段的可见性规则跟首页一致** ——
 * 后端在那里做了角色判断（只有超管/财务能看到金额），非授权角色拿到的金额是空的。
 * 这一点不用 AI 侧再实现一遍，也不要绕过去。
 */
@Component
public class GetDashboardOverviewTool implements AiTool {

    private final DashboardService dashboardService;
    private final ToolResultShaper shaper;

    public GetDashboardOverviewTool(DashboardService dashboardService, ToolResultShaper shaper) {
        this.dashboardService = dashboardService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "get_dashboard_overview";
    }

    @Override
    public String permission() {
        return "dashboard:view";
    }

    @Override
    public String description() {
        return "获取经营概览：指定时间范围内新增的线索/客户/商机/合同数量、销售额/回款额/开票额，以及当前待办数量。"
                + "用户问「最近业绩怎么样」「这个月新增了多少线索」「有多少待办」时用这个。"
                + "注意金额字段只有具备权限的账号（管理员/财务）才拿得到，其他人问金额时拿到的会是空值，"
                + "这时应当如实说明无权限查看，不要编造数字。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("range", ToolArgs.enumProp(
                "统计的时间范围：day=今天 week=本周 month=本月（默认）",
                List.of("day", "week", "month")));
        return ToolArgs.schema(props);
    }

    @Override
    public Object execute(Map<String, Object> args) {
        String range = ToolArgs.str(args, "range");
        DashboardStatsRespVO stats = dashboardService.stats(range);
        DashboardTodoRespVO todo = dashboardService.todo();

        Map<String, Object> row = ToolResultShaper.row();
        // 统计区间的新增数量
        row.put("leadCount", stats.getLeadCount());
        row.put("customerCount", stats.getCustomerCount());
        row.put("opportunityCount", stats.getOpportunityCount());
        row.put("contractCount", stats.getContractCount());
        // 金额：无权限时后端返回空，这里原样透出（description 已交代模型该怎么解释）
        row.put("salesAmount", stats.getSalesAmount());
        row.put("receivedAmount", stats.getReceivedAmount());
        row.put("invoicedAmount", stats.getInvoicedAmount());
        // 当前待办（不受 range 影响，是「此刻」的快照）
        row.put("pendingLead", todo.getPendingLead());
        row.put("pendingVisitCustomer", todo.getPendingVisitCustomer());
        row.put("pendingOpportunity", todo.getPendingOpportunity());
        row.put("pendingContractAudit", todo.getPendingContractAudit());
        row.put("pendingFinanceAudit", todo.getPendingFinanceAudit());
        row.put("pendingInvoice", todo.getPendingInvoice());

        // 聚合一类的结果也套同一个信封（只有一行），让模型始终面对一种结构
        return shaper.envelope(1, List.of(row));
    }
}
