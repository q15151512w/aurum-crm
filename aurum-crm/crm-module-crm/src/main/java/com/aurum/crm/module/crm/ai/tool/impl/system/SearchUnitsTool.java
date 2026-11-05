package com.aurum.crm.module.crm.ai.tool.impl.system;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.system.model.dto.UnitPageReqVO;
import com.aurum.crm.module.system.model.vo.UnitRespVO;
import com.aurum.crm.module.system.service.UnitService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查询计量单位。
 *
 * <p>{@code sys_unit} 是产品模块引用的主数据（{@code crm_product.unit_id} 是引用不是快照），
 * 「这个产品按什么单位卖 / 有几个小数位」这类问题都要落到这里。
 * {@code decimalPlaces} 值得单独回传：它决定数量能填几位小数，
 * 模型在解释「为什么数量显示成 1.00」时需要这个信息。
 *
 * <p>⚠️ {@code sys_unit} <b>不在数据权限白名单里</b>，行级权限不生效 ——
 * {@link #permission()} 返回的 {@code system:unit:list} 是唯一闸门，不能写成 null。
 */
@Component
public class SearchUnitsTool implements AiTool {

    private final UnitService unitService;
    private final ToolResultShaper shaper;

    public SearchUnitsTool(UnitService unitService, ToolResultShaper shaper) {
        this.unitService = unitService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "search_units";
    }

    @Override
    public String permission() {
        return "system:unit:list";
    }

    @Override
    public String description() {
        return "分页查询计量单位（个/台/箱/千克…），返回单位名称 / 编码 / 数量小数位 / 启用状态。"
                + "用户问「有哪些单位」「某个单位的小数位是多少」「哪些单位停用了」时用这个。"
                + "状态是中文（启用/停用），不传表示全部。需要权限 system:unit:list。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string", "关键字，同时匹配单位名称与单位编码"));
        props.put("status", ToolArgs.enumProp("启用状态（中文）", List.of("启用", "停用")));
        props.put("pageNo", ToolArgs.prop("integer", "页码，从 1 开始，最大 5"));
        props.put("pageSize", ToolArgs.prop("integer", "每页条数，最大 10，默认 5"));
        return ToolArgs.schema(props);
    }

    @Override
    public Object execute(Map<String, Object> args) {
        UnitPageReqVO req = new UnitPageReqVO();
        req.setKeyword(ToolArgs.str(args, "keyword"));

        // 中文标签 → 码值：0 停用 / 1 启用。不传就查全部。
        String statusLabel = ToolArgs.str(args, "status");
        if (statusLabel != null) {
            req.setStatus(switch (statusLabel) {
                case "启用" -> 1;
                case "停用" -> 0;
                default -> throw new IllegalArgumentException("启用状态不合法，只能是：启用 / 停用");
            });
        }

        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        PageResult<UnitRespVO> page = unitService.pageQuery(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    private Map<String, Object> toRow(UnitRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        row.put("unitId", vo.getId() == null ? null : String.valueOf(vo.getId()));
        row.put("name", vo.getName());
        row.put("code", vo.getCode());
        row.put("decimalPlaces", vo.getDecimalPlaces());
        row.put("sort", vo.getSort());
        // 码值转中文，避免模型对着 0/1 猜
        row.put("status", vo.getStatus() == null ? null : (vo.getStatus() == 1 ? "启用" : "停用"));
        row.put("creator", vo.getCreator());
        row.put("createTime", vo.getCreateTime());
        return row;
    }
}
