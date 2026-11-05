package com.aurum.crm.module.crm.ai.provider;

import lombok.Data;

/**
 * 一次工具调用 —— 已经是**聚合完成**的形态，不是流式分片。
 *
 * <p>上游在流式下会把一次工具调用拆成很多片（{@code id} 和 {@code name} 只出现在第一片，
 * {@code arguments} 是字符串片段）。那些碎片**不允许**泄漏到这一层以上 ——
 * 聚合是各 {@link AiModelClient} 自己的职责，编排层只看到这个完整的对象。
 */
@Data
public class AiToolCall {

    /** 上游给这次调用的 id，回灌结果时必须原样带回去（{@code tool_call_id}）。 */
    private String id;

    /** 工具名，对应 {@code AiTool#name()}。 */
    private String name;

    /** 已拼接完整的参数 JSON 字符串。模型偶尔会产出非法 JSON，解析失败由编排层兜。 */
    private String arguments;

    public static AiToolCall of(String id, String name, String arguments) {
        AiToolCall call = new AiToolCall();
        call.id = id;
        call.name = name;
        call.arguments = arguments;
        return call;
    }
}
