package com.aurum.crm.module.system.service;

import com.aurum.crm.module.system.model.dto.AiCallLogRecord;

/**
 * AI 调用日志：把每次大模型调用的耗时、token 消耗、成败写进 {@code sys_ai_call_log}。
 *
 * <p>这张表存在的意义是**可观测与成本核算**，不是会话记录 ——
 * 表设计上刻意只存摘要（{@code input_digest} / {@code output_digest}），
 * 不存 prompt / completion 全文，避免用户问的业务数据落库。
 */
public interface AiCallLogService {

    /**
     * 记一条调用日志。
     *
     * <p><b>实现保证不抛异常</b>：写日志失败不能影响用户正在进行的对话 ——
     * 那是「观测手段」反过来破坏了「被观测的业务」，最糟的一种失败方式。
     * 内部会 catch 并降级成 WARN。
     */
    void record(AiCallLogRecord record);
}
