package com.aurum.crm.module.crm.ai.tool;

import cn.hutool.json.JSONUtil;
import com.aurum.crm.module.crm.ai.config.AiProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具结果的统一整形：加信封、控体积。
 *
 * <p>模型看到的结果**必须**是同一个形状。每个工具各返回各的字段时，
 * 模型得先分辨「这次返回的是什么结构」才能回答，既费 token 又容易理解错。
 *
 * <p>信封长这样：
 * <pre>
 * { "total": 137, "returned": 5, "truncated": false, "rows": [ {...}, ... ] }
 * </pre>
 *
 * <p>{@code total} 是**关键字段**：没有它，模型分不清「一共就 3 条」和
 * 「我只看到前 3 条」，会据此给出错误结论（「你名下只有 3 个客户」）。
 */
@Component
public class ToolResultShaper {

    private static final String TRUNCATED_HINT = "结果过多已截断，请收窄查询条件（如加关键字、限定时间范围）后重试。";

    private final AiProperties properties;

    public ToolResultShaper(AiProperties properties) {
        this.properties = properties;
    }

    /**
     * 包成信封，并在超出字符预算时从尾部丢弃行。
     *
     * @param total 数据库里的实际命中总数（不是 {@code rows.size()}）
     * @param rows  本页投影后的行
     */
    public Map<String, Object> envelope(long total, List<Map<String, Object>> rows) {
        int budget = properties.getToolResultMaxChars();
        List<Map<String, Object>> kept = new ArrayList<>(rows.size());
        boolean truncated = false;

        for (Map<String, Object> row : rows) {
            kept.add(row);
            // 逐行试探：刚加进来的这一行如果撑爆预算，就把它退回去。
            // 行数上限只有 10，逐个序列化的开销可以接受，换来的是实现最简单。
            if (JSONUtil.toJsonStr(kept).length() > budget) {
                kept.remove(kept.size() - 1);
                truncated = true;
                break;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("returned", kept.size());
        result.put("truncated", truncated);
        if (truncated) {
            // 明确告诉模型「被截断了、该怎么办」，否则它会拿不完整的结果直接下结论
            result.put("hint", TRUNCATED_HINT);
        }
        result.put("rows", kept);
        return result;
    }

    /** 构造一行投影结果（保持插入顺序，便于人读日志）。 */
    public static Map<String, Object> row() {
        return new LinkedHashMap<>();
    }

    /** 按配置截断字符串字段，避免备注/地址这类长文本吃掉上下文。 */
    public String cut(String text) {
        if (text == null) {
            return null;
        }
        int max = properties.getToolFieldMaxChars();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
