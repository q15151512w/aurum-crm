package com.aurum.crm.module.crm.ai.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具参数的读取与 JSON Schema 的构造。
 *
 * <p><b>为什么取值要这么小心</b>：参数是**大模型生成的**，不是前端表单来的 ——
 * 它可能给出字符串 {@code "5"} 而不是数字 {@code 5}，可能给 {@code null}，
 * 可能干脆不传。直接强转 ({@code (Integer) args.get("pageSize")}) 会抛
 * {@code ClassCastException}，而这类异常在对话里表现为「AI 突然不能查数了」，
 * 很难归因。所有取值都走这里，统一容错。
 */
public final class ToolArgs {

    /**
     * 单次查询最多取第几页。
     *
     * <p>硬上限，与 {@code pageSize} 一起构成「单次最多 10 行」的闸门。
     * 模型很容易把 pageNo 一路加大（它想「多看几条」），
     * 但每多一页就多一份上下文和费用，必须由我们兜住而不是靠它自觉。
     */
    private static final int MAX_PAGE_NO = 5;

    /** 单页最大条数，同上。 */
    private static final int MAX_PAGE_SIZE = 10;

    private static final int DEFAULT_PAGE_SIZE = 5;

    private ToolArgs() {
    }

    // ==================== 取值 ====================

    /** 取字符串；空白视为未传。 */
    public static String str(Map<String, Object> args, String key) {
        if (args == null) {
            return null;
        }
        Object v = args.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    /** 取整数；兼容模型把数字写成字符串的情况。取不到或非法返回 null。 */
    public static Integer intVal(Map<String, Object> args, String key) {
        if (args == null) {
            return null;
        }
        Object v = args.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Integer.valueOf(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** 取长整数；兼容字符串形式。 */
    public static Long longVal(Map<String, Object> args, String key) {
        if (args == null) {
            return null;
        }
        Object v = args.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Long.valueOf(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 取必填的长整数，缺失或非法时抛异常。
     *
     * <p>详情类工具的 id 是必填的 —— 缺失时必须报错让模型重新问用户，
     * 而不是「查不到就返回空」，那会让模型以为「这个 id 没有数据」。
     */
    public static Long requiredLong(Map<String, Object> args, String key) {
        Long v = longVal(args, key);
        if (v == null) {
            throw new IllegalArgumentException("缺少必填参数：" + key);
        }
        return v;
    }

    // ==================== 分页夹紧 ====================

    /** 页码，夹到 {@code [1, MAX_PAGE_NO]}。 */
    public static int pageNo(Map<String, Object> args) {
        Integer v = intVal(args, "pageNo");
        if (v == null || v < 1) {
            return 1;
        }
        return Math.min(v, MAX_PAGE_NO);
    }

    /** 每页条数，夹到 {@code [1, MAX_PAGE_SIZE]}。 */
    public static int pageSize(Map<String, Object> args) {
        Integer v = intVal(args, "pageSize");
        if (v == null || v < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(v, MAX_PAGE_SIZE);
    }

    // ==================== JSON Schema 构造 ====================

    /** 构造参数对象的 Schema。 */
    public static Map<String, Object> schema(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required.length > 0) {
            schema.put("required", List.of(required));
        }
        return schema;
    }

    /** 一个普通属性。 */
    public static Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }

    /**
     * 取值受限的字符串属性。
     *
     * <p>枚举约束比在 description 里写「只能是 X/Y/Z」可靠得多 ——
     * 模型对结构化约束的遵循度明显更高。
     */
    public static Map<String, Object> enumProp(String description, List<String> values) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "string");
        p.put("description", description);
        p.put("enum", new ArrayList<>(values));
        return p;
    }

    /** 用于 {@link #schema} 的可变属性容器（{@code Map.of} 不支持超过 10 项，且顺序不固定）。 */
    public static Map<String, Object> props() {
        return new LinkedHashMap<>();
    }
}
