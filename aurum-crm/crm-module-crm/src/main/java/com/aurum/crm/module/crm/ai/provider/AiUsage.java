package com.aurum.crm.module.crm.ai.provider;

import lombok.Data;

/**
 * Token 用量，写进 {@code sys_ai_call_log} 用于看成本。
 *
 * <p>上游只在**最后一帧**给用量；流中途断开时拿不到，这时三个值都是 0 ——
 * 日志里 0 表示「没拿到」而不是「没消耗」，排查成本异常时要知道这个区别。
 */
@Data
public class AiUsage {

    private int promptTokens;
    private int completionTokens;
    private int totalTokens;

    public static AiUsage of(int promptTokens, int completionTokens, int totalTokens) {
        AiUsage usage = new AiUsage();
        usage.promptTokens = promptTokens;
        usage.completionTokens = completionTokens;
        usage.totalTokens = totalTokens;
        return usage;
    }
}
