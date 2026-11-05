package com.aurum.crm.module.system.model.dto;

import lombok.Data;

/**
 * 写一条 AI 调用日志所需的信息。
 *
 * <p>为什么不直接让调用方 new 一个 {@code AiCallLogDO}：DO 里有 {@code scene}、
 * {@code cost}、{@code bizType} 这些**本场景恒定的字段**，让调用方每次自己填，
 * 迟早会有人填错或漏填。放在这里由 Service 统一兜住。
 *
 * <p>{@code operatorId} 由调用方**显式传入**，Service 不读 {@code LoginUserHolder} ——
 * 这样日志写入与线程上下文解耦，将来若在别的线程补日志也不会写错人。
 */
@Data
public class AiCallLogRecord {

    /** 操作人ID */
    private Long operatorId;

    /** 模型名，如 {@code deepseek-chat} */
    private String modelName;

    private int promptTokens;
    private int completionTokens;
    private int totalTokens;

    /** 耗时（毫秒）：从发起上游请求到流结束 */
    private long latencyMs;

    /** 是否成功 */
    private boolean success;

    /** 失败原因（成功时为 null） */
    private String errorMsg;

    /** 输入摘要（提问 + 关键参数），Service 会截断 */
    private String inputDigest;

    /** 输出摘要（模型回答），Service 会截断 */
    private String outputDigest;
}
