package com.aurum.crm.module.crm.ai.tool.impl;

import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.crm.model.vo.OpportunityFunnelRespVO;
import com.aurum.crm.module.crm.service.DashboardService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 销售漏斗：各商机阶段的数量与金额。
 *
 * <p>返回的行**包含数量为 0 的阶段**（后端就是这样构造的）——
 * 对漏斗来说「某阶段是 0」和「某阶段不存在」是两条完全不同的信息，
 * 过滤掉空阶段会让模型给出「可能没有这个阶段」这种含糊结论。
 */
@Component
public class GetSalesFunnelTool implements AiTool {

    private final DashboardService dashboardService;
    private final ToolResultShaper shaper;

    public GetSalesFunnelTool(DashboardService dashboardService, ToolResultShaper shaper) {
        this.dashboardService = dashboardService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "get_sales_funnel";
    }

    @Override
    public String permission() {
        return "dashboard:view";
    }

    @Override
    public String description() {
        return "获取销售漏斗：商机在「初步接洽/需求确认/方案报价/商务谈判/赢单/输单」各阶段的数量与金额。"
                + "用户问「漏斗什么样」「各阶段有多少商机」「卡在哪个阶段」时用这个。"
                + "返回里包含数量为 0 的阶段，这是有意义的（说明该阶段确实没有商机）。";
    }

    @Override
    public Map<String, Object> parameters() {
        // 无参工具：显式给出空 schema，而不是返回 null ——
        // 有些上游对 null 参数的容忍度不一致，给个空 object 最稳
        return ToolArgs.schema(ToolArgs.props());
    }

    @Override
    public Object execute(Map<String, Object> args) {
        List<OpportunityFunnelRespVO> funnel = dashboardService.funnel();
        List<Map<String, Object>> rows = new ArrayList<>(funnel.size());
        for (OpportunityFunnelRespVO stage : funnel) {
            Map<String, Object> row = ToolResultShaper.row();
            row.put("stage", stage.getStage());
            row.put("count", stage.getCount());
            row.put("amount", stage.getAmount());
            rows.add(row);
        }
        return shaper.envelope(rows.size(), rows);
    }
}
