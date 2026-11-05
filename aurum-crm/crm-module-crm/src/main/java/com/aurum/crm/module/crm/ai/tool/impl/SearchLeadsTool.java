package com.aurum.crm.module.crm.ai.tool.impl;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.crm.model.DictLabels;
import com.aurum.crm.module.crm.model.dto.LeadPageReqDTO;
import com.aurum.crm.module.crm.model.vo.LeadRespVO;
import com.aurum.crm.module.crm.service.LeadService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 按条件查询线索。
 *
 * <p>走 {@link LeadService#pageQuery}，因此**数据权限自动生效** ——
 * 销售专员问「所有线索」拿到的也只是他自己（加线索池）范围内的，
 * 不是全公司的。这不是靠本类做了什么，而是因为查询走的是 MyBatis Mapper。
 */
@Component
public class SearchLeadsTool implements AiTool {

    private final LeadService leadService;
    private final ToolResultShaper shaper;

    public SearchLeadsTool(LeadService leadService, ToolResultShaper shaper) {
        this.leadService = leadService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "search_leads";
    }

    @Override
    public String permission() {
        return "crm:lead:list";
    }

    @Override
    public String description() {
        return "按条件分页查询线索。用户问「有多少线索」「我负责的线索」「最近的线索」「找一下XX公司的线索」时用这个。"
                + "返回 total（符合条件总数）与 rows（当前页明细）。"
                + "结果受当前登录人的数据权限限制，看不到无权查看的线索是正常的。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string", "关键字，匹配线索名称/联系人姓名/联系电话"));
        // ⚠️ 这两行的码值必须与 sql/crm.sql 的 sys_dict_data 逐字对齐。
        //    原先写的是「4地推 5转介绍 6广告」和行业「6零售 7建筑」—— 与字典不符，
        //    模型照着错的映射筛选和理解结果，会静默给出错误答案（不报错、看不出来）。
        //    改字典种子时记得回来对一遍这里。
        props.put("source", ToolArgs.prop("integer", "线索来源码值：1官网 2电话 3展会 4转介绍 5广告 6地推 7其他"));
        props.put("industry", ToolArgs.prop("integer", "客户行业码值：1制造业 2互联网 3金融 4教育 5医疗 6政府 7零售 8其他"));
        props.put("scope", ToolArgs.enumProp(
                "取数口径。不传等同于 all（在数据权限范围内不限）",
                List.of("all", "mine", "subordinate", "starred")));
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
        req.setScope(ToolArgs.str(args, "scope"));
        // ⚠️ 分页必须由 ToolArgs 夹紧后再传：模型很容易把 pageSize 开到 100，
        //    那会把上下文和费用一起吃光
        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        PageResult<LeadRespVO> page = leadService.pageQuery(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    private Map<String, Object> toRow(LeadRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        // ⚠️ ID 用字符串：雪花 ID 有 19 位，超出 JS 的安全整数范围（2^53）。
        //    与后端给前端的处理一致（见 JacksonConfig 的 JsSafeLongSerializer），
        //    避免任何一环把它当 double 解析后精度丢失、回传一个错的 id。
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
        row.put("ownerName", vo.getOwnerName());
        row.put("nextContactTime", vo.getNextContactTime());
        row.put("lastFollowTime", vo.getLastFollowTime());
        row.put("createTime", vo.getCreateTime());
        row.put("remark", shaper.cut(vo.getRemark()));
        return row;
    }
}
