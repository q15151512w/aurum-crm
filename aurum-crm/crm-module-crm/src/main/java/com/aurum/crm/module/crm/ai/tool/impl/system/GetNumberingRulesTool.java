package com.aurum.crm.module.crm.ai.tool.impl.system;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.system.model.dto.NumberingRulePageReqVO;
import com.aurum.crm.module.system.model.vo.NumberingRuleRespVO;
import com.aurum.crm.module.system.service.NumberingRuleService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查询编号规则。
 *
 * <p>回答「合同号是怎么生成的」「客户编号的前缀是什么」这类问题时用得上。
 * {@code example} 是后端按规则算出的示例编号，比 {@code prefix + dateFormat + seqLength}
 * 三个字段更直观 —— 模型拿它能直接说「长这样：HT202609150001」，
 * 所以即使它是派生值也值得回传。
 *
 * <p>{@code items}（规则明细：哪些片段参与拼接）**故意不回传**：
 * 它是嵌套集合，体积大且对「编号长什么样」没有增量价值，
 * 与 ToolResultShaper 控体积的初衷一致。
 *
 * <p>⚠️ {@code sys_numbering_rule} <b>不在数据权限白名单里</b>，行级权限不生效 ——
 * {@link #permission()} 返回的 {@code system:numberingRule:list} 是唯一闸门，不能写成 null。
 */
@Component
public class GetNumberingRulesTool implements AiTool {

    private final NumberingRuleService numberingRuleService;
    private final ToolResultShaper shaper;

    public GetNumberingRulesTool(NumberingRuleService numberingRuleService, ToolResultShaper shaper) {
        this.numberingRuleService = numberingRuleService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "get_numbering_rules";
    }

    @Override
    public String permission() {
        return "system:numberingRule:list";
    }

    @Override
    public String description() {
        return "分页查询单据编号生成规则，返回规则名称 / 编码 / 前缀 / 日期格式 / 流水号位数 / 重置周期 / "
                + "是否启用 / 示例编号。用户问「合同号是怎么生成的」「编号规则有哪些」「客户编号长什么样」时用这个。"
                + "example 字段是后端算好的示例，直接引用它即可。需要权限 system:numberingRule:list。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string", "关键字，匹配规则名称或规则编码"));
        props.put("pageNo", ToolArgs.prop("integer", "页码，从 1 开始，最大 5"));
        props.put("pageSize", ToolArgs.prop("integer", "每页条数，最大 10，默认 5"));
        return ToolArgs.schema(props);
    }

    @Override
    public Object execute(Map<String, Object> args) {
        NumberingRulePageReqVO req = new NumberingRulePageReqVO();
        req.setKeyword(ToolArgs.str(args, "keyword"));
        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        PageResult<NumberingRuleRespVO> page = numberingRuleService.page(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    private Map<String, Object> toRow(NumberingRuleRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        row.put("ruleId", vo.getId() == null ? null : String.valueOf(vo.getId()));
        row.put("name", vo.getName());
        row.put("code", vo.getCode());
        row.put("prefix", vo.getPrefix());
        row.put("dateFormat", vo.getDateFormat());
        row.put("seqLength", vo.getSeqLength());
        row.put("resetCycle", vo.getResetCycle());
        row.put("enabled", vo.getEnabled());
        // 示例编号比「前缀+日期格式+位数」三个字段加起来更好懂
        row.put("example", vo.getExample());
        return row;
    }
}
