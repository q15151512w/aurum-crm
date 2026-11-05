package com.aurum.crm.module.crm.ai.session;

import lombok.Data;

import java.util.List;

/**
 * 存进 Redis 的一条会话消息。
 *
 * <p>只存**用户能看见的** user / assistant 两类。工具调用过程中的中间消息
 * （`tool_calls` / `tool` 结果）不进存储 —— 那是单次请求内部的往返，
 * 不是对话的一部分。但会记录 assistant 这轮**用过哪些工具**（{@link #tools}），
 * 这样刷新后重新渲染历史时，还能显示出「查询客户」这类小胶囊，
 * 与实时对话时的观感一致。
 */
@Data
public class AiStoredMessage {

    /** user / assistant */
    private String role;

    /** 消息正文 */
    private String content;

    /** assistant 专用：本轮用过的工具名，用于重新渲染时显示 */
    private List<String> tools;

    /** 写入时间（毫秒），仅用于前端展示顺序之外的排查 */
    private Long ts;
}
