package com.aurum.crm.module.crm.ai.provider;

import lombok.Data;

import java.util.List;

/**
 * 一条对话消息（提供商无关）。
 *
 * <p>四种角色里，{@code assistant} 与 {@code tool} 是**工具调用专用**的：
 * <ul>
 *   <li>{@code assistant} + {@link #toolCalls}：模型决定要调哪些工具</li>
 *   <li>{@code tool} + {@link #toolCallId}：我们回灌的工具执行结果，必须与上面的调用一一配对</li>
 * </ul>
 * 少回一条 {@code tool} 结果、或 {@code toolCallId} 对不上，上游会直接返回 400。
 * 这不是「宽松解析」能糊过去的，配对关系必须严格维护。
 */
@Data
public class AiMessage {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";

    private String role;
    private String content;

    /** {@code assistant} 专用：本轮决定调用的工具。 */
    private List<AiToolCall> toolCalls;

    /** {@code tool} 专用：这条结果对应哪一次调用。 */
    private String toolCallId;

    public static AiMessage system(String content) {
        return build(ROLE_SYSTEM, content, null, null);
    }

    public static AiMessage user(String content) {
        return build(ROLE_USER, content, null, null);
    }

    /**
     * assistant 消息。{@code toolCalls} 为 {@code null} 表示这条是纯文本回复。
     *
     * <p>⚠️ 调工具那一轮，{@code content} 通常是空串但**不能传 null** ——
     * 有些上游会因为 content 缺失而报 400，传空串是安全的。
     */
    public static AiMessage assistant(String content, List<AiToolCall> toolCalls) {
        return build(ROLE_ASSISTANT, content == null ? "" : content, toolCalls, null);
    }

    public static AiMessage tool(String toolCallId, String content) {
        return build(ROLE_TOOL, content, null, toolCallId);
    }

    private static AiMessage build(String role, String content, List<AiToolCall> toolCalls, String toolCallId) {
        AiMessage m = new AiMessage();
        m.role = role;
        m.content = content;
        m.toolCalls = toolCalls;
        m.toolCallId = toolCallId;
        return m;
    }
}
