package com.aurum.crm.module.crm.ai.tool.impl;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.crm.model.dto.OpportunityPageReqVO;
import com.aurum.crm.module.crm.model.vo.OpportunityRespVO;
import com.aurum.crm.module.crm.service.OpportunityService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 按条件查询商机。
 *
 * <p>{@code stage} 传的是**中文标签**而不是码值：{@code OpportunityServiceImpl#resolveStage}
 * 就是用 {@code OpportunityStage.ofLabel} 解析的，而且标签对模型更自然。
 * 传了非法标签会抛异常（消息里带可选值清单），由编排层回灌给模型让它自己改。
 */
@Component
public class SearchOpportunitiesTool implements AiTool {

    private final OpportunityService opportunityService;
    private final ToolResultShaper shaper;

    public SearchOpportunitiesTool(OpportunityService opportunityService, ToolResultShaper shaper) {
        this.opportunityService = opportunityService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "search_opportunities";
    }

    @Override
    public String permission() {
        return "crm:opportunity:list";
    }

    @Override
    public String description() {
        return "按条件分页查询商机。用户问「有多少商机」「某客户有哪些商机」「处于商务谈判阶段的商机」时用这个。"
                + "返回 total（符合条件总数）与 rows（当前页明细）。"
                + "结果受当前登录人的数据权限限制。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string", "关键字，匹配商机名称"));
        props.put("stage", ToolArgs.enumProp(
                "商机阶段（中文）",
                List.of("初步接洽", "需求确认", "方案报价", "商务谈判", "赢单", "输单")));
        props.put("customerId", ToolArgs.prop("string", "限定某个客户的商机（来自 search_customers 的 customerId）"));
        props.put("scope", ToolArgs.enumProp(
                "取数口径。不传则不限",
                List.of("mine", "subordinate", "starred")));
        props.put("pageNo", ToolArgs.prop("integer", "页码，从 1 开始，最大 5"));
        props.put("pageSize", ToolArgs.prop("integer", "每页条数，最大 10，默认 5"));
        return ToolArgs.schema(props);
    }

    @Override
    public Object execute(Map<String, Object> args) {
        OpportunityPageReqVO req = new OpportunityPageReqVO();
        req.setKeyword(ToolArgs.str(args, "keyword"));
        req.setStage(ToolArgs.str(args, "stage"));
        req.setCustomerId(ToolArgs.longVal(args, "customerId"));
        req.setScope(ToolArgs.str(args, "scope"));
        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        PageResult<OpportunityRespVO> page = opportunityService.pageQuery(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    private Map<String, Object> toRow(OpportunityRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        row.put("opportunityId", vo.getId() == null ? null : String.valueOf(vo.getId()));
        row.put("opportunityNo", vo.getOpportunityNo());
        row.put("name", vo.getName());
        row.put("customerId", vo.getCustomerId() == null ? null : String.valueOf(vo.getCustomerId()));
        row.put("customerName", vo.getCustomerName());
        // stage 后端已经转成中文标签了（见 OpportunityRespVO），直接用
        row.put("stage", vo.getStage());
        row.put("ownerName", vo.getOwnerName());
        row.put("finalAmount", vo.getFinalAmount());
        row.put("expectDealTime", vo.getExpectDealTime());
        row.put("nextContactTime", vo.getNextContactTime());
        row.put("actualCloseDate", vo.getActualCloseDate());
        row.put("creator", vo.getCreator());
        row.put("createTime", vo.getCreateTime());
        row.put("remark", shaper.cut(vo.getRemark()));
        return row;
    }
}
