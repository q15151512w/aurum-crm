package com.aurum.crm.module.crm.ai.provider;

import lombok.Data;

import java.util.List;

/**
 * 流式响应的一帧（提供商无关）。
 *
 * <p><b>关于 {@link #toolCalls} 的约定（重要）</b>：它不是上游的原始分片，而是
 * **已经聚合完整的**工具调用列表，并且**只在流末尾出现一次**（配合
 * {@code finishReason == "tool_calls"}）。分片的拼接由各 {@code AiModelClient}
 * 自己完成 —— 编排层永远看不到 {@code arguments} 的字符串片段，
 * 也就不需要知道「OpenAI 的 arguments 是流式拼接的」这类上游细节。
 *
 * <p>对应地，{@link #contentDelta} 是**增量**而非全量：每帧只带这一帧新增的正文。
 */
@Data
public class AiStreamChunk {

    /**
     * 正文增量。
     *
     * <p>可能为 {@code null} 或空串 —— 上游在工具调用轮会发空的 content delta，
     * 调用方**必须先判空再转发**，否则前端会多出一堆空气泡。
     */
    private String contentDelta;

    /** 聚合完成的工具调用；为空表示这一帧没有工具调用信息。 */
    private List<AiToolCall> toolCalls;

    /** 结束原因，如 {@code stop} / {@code tool_calls} / {@code length}。中间帧为 null。 */
    private String finishReason;

    /** Token 用量，只在最后一帧出现。 */
    private AiUsage usage;

    public static AiStreamChunk content(String delta) {
        AiStreamChunk c = new AiStreamChunk();
        c.contentDelta = delta;
        return c;
    }

    public static AiStreamChunk tools(List<AiToolCall> toolCalls, String finishReason) {
        AiStreamChunk c = new AiStreamChunk();
        c.toolCalls = toolCalls;
        c.finishReason = finishReason;
        return c;
    }

    public static AiStreamChunk finished(String finishReason) {
        AiStreamChunk c = new AiStreamChunk();
        c.finishReason = finishReason;
        return c;
    }

    public static AiStreamChunk usage(AiUsage usage) {
        AiStreamChunk c = new AiStreamChunk();
        c.usage = usage;
        return c;
    }
}
