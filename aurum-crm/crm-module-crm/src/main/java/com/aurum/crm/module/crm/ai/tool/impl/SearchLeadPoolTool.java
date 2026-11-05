package com.aurum.crm.module.crm.ai.tool.impl;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.crm.model.DictLabels;
import com.aurum.crm.module.crm.model.dto.LeadPageReqDTO;
import com.aurum.crm.module.crm.model.vo.LeadRespVO;
import com.aurum.crm.module.crm.service.LeadPoolService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查询线索池（没人负责、可被领取的线索）。
 *
 * <p><b>和 {@code search_leads} 的区别要讲清楚</b>，否则模型分不清该调哪个：
 * {@code search_leads} 查的是**在跟的**线索（有负责人），
 * 本工具查的是**池子里的**（{@code owner_user_id} 恒为空）。
 * 用户问「线索池里有多少」「有多少没人领的线索」用这个。
 *
 * <p>⚠️ 池内线索的负责人恒为空，所以**数据权限对它没有收窄作用**
 * （{@code LeadPoolService.poolPage} 的注释：线索池对全员可见）。
 * 能看见它的前提是功能权限 {@code crm:leadPool:list}。
 */
@Component
public class SearchLeadPoolTool implements AiTool {

    private final LeadPoolService leadPoolService;
    private final ToolResultShaper shaper;

    public SearchLeadPoolTool(LeadPoolService leadPoolService, ToolResultShaper shaper) {
        this.leadPoolService = leadPoolService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "search_lead_pool";
    }

    @Override
    public String permission() {
        return "crm:leadPool:list";
    }

    @Override
    public String description() {
        return "查询线索池里**没人负责**的线索（可被领取的那些）。"
                + "用户问「线索池有多少」「有多少没人领的线索」「池子里某来源的线索」时用这个。"
                + "⚠️ 别和 search_leads 混：那个查的是在跟的线索（有负责人），这个查的是池子里的（无负责人）。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string", "关键字，匹配线索名称/负责人"));
        props.put("source", ToolArgs.prop("integer", "线索来源码值：1官网 2电话 3展会 4转介绍 5广告 6地推 7其他"));
        props.put("industry", ToolArgs.prop("integer", "所属行业码值：1制造业 2互联网 3金融 4教育 5医疗 6政府 7零售 8其他"));
        props.put("pageNo", ToolArgs.prop("integer", "页码，从 1 开始，最大 5"));
        props.put("pageSize", ToolArgs.prop("integer", "每页条数，最大 10，默认 5"));
        return ToolArgs.schema(props);
    }

    @Override
    public Object execute(Map<String, Object> args) {
        LeadPageReqDTO req = new LeadPageReqDTO();
        req.setKeyword(ToolArgs.str(args, "keyword"));
        req.setSource(ToolArgs.intVal(args, "source"));
        req.setIndustry(ToolArgs.intVal(args, "industry"));
        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        PageResult<LeadRespVO> page = leadPoolService.poolPage(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    private Map<String, Object> toRow(LeadRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        row.put("leadId", vo.getLeadId() == null ? null : String.valueOf(vo.getLeadId()));
        row.put("leadNo", vo.getLeadNo());
        row.put("leadName", vo.getLeadName());
        row.put("contactName", vo.getContactName());
        row.put("phone", vo.getPhone());
        // 码值必须转成中文再给模型：直接给 "3" 它只能瞎猜，会答成「来源是3」
        row.put("source", DictLabels.source(vo.getSource()));
        row.put("industry", DictLabels.industry(vo.getIndustry()));
        row.put("level", DictLabels.level(vo.getLevel()));
        row.put("region", vo.getRegion());
        // —— 池专属字段（这也是本工具与 search_leads 的差别所在）——
        row.put("enterPoolReason", DictLabels.poolReason(vo.getEnterPoolReasonCode()));
        row.put("enterPoolRemark", shaper.cut(vo.getEnterPoolRemark()));
        row.put("prevOwnerName", vo.getPrevOwnerName());
        row.put("enterPoolTime", vo.getEnterPoolTime());
        return row;
    }
}
