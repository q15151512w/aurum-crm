package com.aurum.crm.module.crm.ai.tool.impl;

import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.crm.model.DictLabels;
import com.aurum.crm.module.crm.model.vo.LeadRespVO;
import com.aurum.crm.module.crm.service.LeadService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查一条线索的完整信息。
 *
 * <p>与 {@link SearchLeadsTool} 分开而不是「搜索只传 id 就返回详情」：
 * 模型需要能明确区分「我要找一批」和「我要看这一条的细节」，
 * 分开后工具描述更清楚，模型选错的概率更低。
 *
 * <p>线索不存在时 {@code LeadService#get} 会抛 {@code LEAD_NOT_EXISTS}，
 * 由编排层捕获后回灌给模型 —— 模型会据此告诉用户「没找到这条线索」，
 * 而不是编一个出来。
 */
@Component
public class GetLeadDetailTool implements AiTool {

    private final LeadService leadService;
    private final ToolResultShaper shaper;

    public GetLeadDetailTool(LeadService leadService, ToolResultShaper shaper) {
        this.leadService = leadService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "get_lead_detail";
    }

    @Override
    public String permission() {
        return "crm:lead:list";
    }

    @Override
    public String description() {
        return "根据线索ID查询单条线索的完整信息。"
                + "线索ID来自 search_leads 的返回结果，不要自己编造ID。"
                + "用户问「这条线索的详情」「某条线索的联系人是谁」时用这个。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("leadId", ToolArgs.prop("string", "线索ID（来自 search_leads 返回的 leadId，是字符串形式的数字）"));
        return ToolArgs.schema(props, "leadId");
    }

    @Override
    public Object execute(Map<String, Object> args) {
        Long leadId = ToolArgs.requiredLong(args, "leadId");
        LeadRespVO vo = leadService.get(leadId);

        Map<String, Object> row = ToolResultShaper.row();
        row.put("leadId", vo.getLeadId() == null ? null : String.valueOf(vo.getLeadId()));
        row.put("leadNo", vo.getLeadNo());
        row.put("leadName", vo.getLeadName());
        row.put("contactName", vo.getContactName());
        row.put("position", vo.getPosition());
        row.put("phone", vo.getPhone());
        row.put("email", vo.getEmail());
        row.put("wechat", vo.getWechat());
        row.put("source", DictLabels.source(vo.getSource()));
        row.put("industry", DictLabels.industry(vo.getIndustry()));
        row.put("level", DictLabels.level(vo.getLevel()));
        row.put("region", vo.getRegion());
        row.put("address", shaper.cut(vo.getAddress()));
        row.put("ownerName", vo.getOwnerName());
        row.put("nextContactTime", vo.getNextContactTime());
        row.put("lastFollowTime", vo.getLastFollowTime());
        row.put("enterPoolTime", vo.getEnterPoolTime());
        row.put("convertedCustomerId", vo.getConvertedCustomerId() == null
                ? null : String.valueOf(vo.getConvertedCustomerId()));
        row.put("creator", vo.getCreator());
        row.put("createTime", vo.getCreateTime());
        row.put("remark", shaper.cut(vo.getRemark()));

        // 与列表保持同一个信封形状：模型只需要认一种结构
        return shaper.envelope(1, List.of(row));
    }
}
