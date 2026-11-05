package com.aurum.crm.module.crm.ai.tool.impl.system;

import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.system.model.vo.DictDataRespVO;
import com.aurum.crm.module.system.service.DictService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 查询字典项。
 *
 * <p><b>用途是「让模型看懂码值」而不是「查配置」</b>：业务表里到处是
 * {@code status=1} / {@code level=2} 这类码值，模型看到 1 只会猜。
 * 先调本工具把码值翻成中文标签，再回答「这批客户都是什么级别」才靠谱。
 *
 * <p><b>为什么必须自己封顶</b>：{@code dataByTypes} 不是分页接口，
 * 传 10 个类型可能拉回上千行字典项，既超上下文又超预算。
 * 这里硬性截断到 {@link #MAX_ROWS} 行，并把**真实行数**放进信封的 {@code total}，
 * 让模型看得出「被截断了」而不是「这个字典就这么几项」。
 *
 * <p>⚠️ 权限点用的是管理端的 {@code system:dict:list}，而 {@code /dict-data/batch}
 * 这条接口本身是**对所有登录用户开放**的（前端靠它缓存下拉选项）。
 * 也就是说本工具比对应接口更严 —— 这是刻意的：AI 场景下「把全量字典表吐给模型」
 * 没必要对所有角色开放，需要的人自然会拿到这个权限。
 *
 * <p>⚠️ {@code sys_dict_data} <b>不在数据权限白名单里</b>，行级权限不生效 ——
 * {@link #permission()} 是唯一闸门，不能写成 null。
 */
@Component
public class GetDictDataTool implements AiTool {

    /** 单次返回的最大行数（跨全部请求类型合计），理由见类注释。 */
    private static final int MAX_ROWS = 200;

    private final DictService dictService;
    private final ToolResultShaper shaper;

    public GetDictDataTool(DictService dictService, ToolResultShaper shaper) {
        this.dictService = dictService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "get_dict_data";
    }

    @Override
    public String permission() {
        return "system:dict:list";
    }

    @Override
    public String description() {
        return "按字典类型编码批量查询字典项，把业务数据里的码值翻译成中文标签。"
                + "当用户问「XX 是什么意思」、或你需要解释某个 status / level / type 数字代表什么时用这个。"
                + "必须传 types（字典类型编码，逗号分隔，如 customer_level,industry）；"
                + "只返回**启用**的字典项。返回行数按类型分组拍平，超过 200 行会截断。"
                + "需要权限 system:dict:list。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("types", ToolArgs.prop("string",
                "字典类型编码，多个用英文逗号分隔，如 customer_level,industry,lead_source"));
        return ToolArgs.schema(props, "types");
    }

    @Override
    public Object execute(Map<String, Object> args) {
        String raw = ToolArgs.str(args, "types");
        if (raw == null) {
            // 类型是必填的：Service 对空集合直接返回空 Map（不查库），
            // 那样模型会得到「字典是空的」这个错误结论，不如报错让它重新问用户。
            throw new IllegalArgumentException("缺少必填参数：types（字典类型编码，多个用逗号分隔）");
        }
        List<String> types = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (types.isEmpty()) {
            throw new IllegalArgumentException("types 不能为空，请给出至少一个字典类型编码");
        }

        Map<String, List<DictDataRespVO>> grouped = dictService.dataByTypes(types);

        // 按类型拍平：一行 = 一个字典项。类型编码挂到行上，
        // 否则模型分不清「这条属于哪个字典」。
        List<Map<String, Object>> all = new ArrayList<>();
        for (Map.Entry<String, List<DictDataRespVO>> entry : grouped.entrySet()) {
            List<DictDataRespVO> items = entry.getValue();
            if (items == null) {
                continue;
            }
            for (DictDataRespVO item : items) {
                all.add(toRow(entry.getKey(), item));
            }
        }

        long total = all.size();
        List<Map<String, Object>> rows = total > MAX_ROWS
                ? new ArrayList<>(all.subList(0, MAX_ROWS))
                : all;

        // total 是真实行数，模型看到 total > returned 就知道被截断了
        Map<String, Object> result = shaper.envelope(total, rows);
        if (total > MAX_ROWS) {
            result.put("truncated", true);
            result.put("hint", "字典项超过 " + MAX_ROWS + " 行已截断，请减少 types 里的类型数量后重试。");
        }
        // 明确告诉模型「查了哪些类型」，便于它判断某个码值是不是「没查到」（类型名写错了）
        result.put("requestedTypes", types);
        result.put("foundTypes", new ArrayList<>(grouped.keySet()));
        return result;
    }

    private Map<String, Object> toRow(String type, DictDataRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        row.put("dictType", type);
        row.put("label", vo.getLabel());
        row.put("value", vo.getValue());
        row.put("sort", vo.getSort());
        row.put("isDefault", vo.getIsDefault());
        row.put("listClass", vo.getListClass());
        row.put("remark", shaper.cut(vo.getRemark()));
        return row;
    }
}
