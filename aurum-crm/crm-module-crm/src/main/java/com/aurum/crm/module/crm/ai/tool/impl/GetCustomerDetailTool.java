package com.aurum.crm.module.crm.ai.tool.impl;

import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.crm.model.DictLabels;
import com.aurum.crm.module.crm.model.vo.CustomerRespVO;
import com.aurum.crm.module.crm.service.CustomerService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查一条客户的完整信息。
 *
 * <p>客户不存在时 {@code CustomerService#get} 会抛异常，由编排层捕获回灌给模型。
 */
@Component
public class GetCustomerDetailTool implements AiTool {

    private final CustomerService customerService;
    private final ToolResultShaper shaper;

    public GetCustomerDetailTool(CustomerService customerService, ToolResultShaper shaper) {
        this.customerService = customerService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "get_customer_detail";
    }

    @Override
    public String permission() {
        return "crm:customer:list";
    }

    @Override
    public String description() {
        return "根据客户ID查询单条客户的完整信息。"
                + "客户ID来自 search_customers 的返回结果，不要自己编造ID。"
                + "用户问某个客户的电话/地址/行业/负责人时用这个。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("customerId", ToolArgs.prop("string", "客户ID（来自 search_customers 返回的 customerId，是字符串形式的数字）"));
        return ToolArgs.schema(props, "customerId");
    }

    @Override
    public Object execute(Map<String, Object> args) {
        Long customerId = ToolArgs.requiredLong(args, "customerId");
        CustomerRespVO vo = customerService.get(customerId);

        Map<String, Object> row = ToolResultShaper.row();
        row.put("customerId", vo.getCustomerId() == null ? null : String.valueOf(vo.getCustomerId()));
        row.put("customerNo", vo.getCustomerNo());
        row.put("customerName", vo.getCustomerName());
        row.put("phone", vo.getPhone());
        row.put("email", vo.getEmail());
        row.put("website", vo.getWebsite());
        row.put("source", DictLabels.source(vo.getSource()));
        row.put("industry", DictLabels.industry(vo.getIndustry()));
        row.put("level", DictLabels.level(vo.getLevel()));
        row.put("dealStatus", DictLabels.dealStatus(vo.getDealStatus()));
        row.put("region", vo.getRegion());
        row.put("address", shaper.cut(vo.getAddress()));
        row.put("ownerName", vo.getOwnerName());
        row.put("leadId", vo.getLeadId() == null ? null : String.valueOf(vo.getLeadId()));
        row.put("enterPoolTime", vo.getEnterPoolTime());
        row.put("lastFollowTime", vo.getLastFollowTime());
        // ⚠️ CustomerRespVO 没有 creator 字段（线索/商机/合同的 VO 才有），别照抄那几个工具
        row.put("createTime", vo.getCreateTime());
        row.put("remark", shaper.cut(vo.getRemark()));

        return shaper.envelope(1, List.of(row));
    }
}
