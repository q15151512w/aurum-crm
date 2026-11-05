package com.aurum.crm.module.crm.ai.provider;

import lombok.Data;

import java.util.List;

/**
 * 一次模型调用请求（提供商无关）。
 *
 * <p>{@link #tools} 为 {@code null} 或空表示**本轮不带工具** ——
 * 编排层在最后一轮会这么传，逼模型输出自然语言结论，
 * 而不是继续要工具、把空结果丢给用户。
 */
@Data
public class AiChatRequest {

    private String model;
    private Double temperature;
    private Integer maxTokens;

    /** 完整对话：system + 历史 + 本轮 user，以及工具调用过程中追加的 assistant / tool 消息。 */
    private List<AiMessage> messages;

    /** 本轮可用的工具；为空表示不给工具。 */
    private List<AiToolSpec> tools;

    public boolean hasTools() {
        return tools != null && !tools.isEmpty();
    }

    public static AiChatRequest of(String model, Double temperature, Integer maxTokens,
                                   List<AiMessage> messages, List<AiToolSpec> tools) {
        AiChatRequest r = new AiChatRequest();
        r.model = model;
        r.temperature = temperature;
        r.maxTokens = maxTokens;
        r.messages = messages;
        r.tools = tools;
        return r;
    }
}
