package com.aurum.crm.module.crm.ai.session;

import lombok.Data;

/**
 * 会话列表里的一项（不含消息正文）。
 *
 * <p>列表接口**不能返回消息内容** —— 会话多了以后那是几 MB 的无用载荷。
 * 标题取首轮提问的前若干字，够用户认出「这是哪次对话」即可。
 */
@Data
public class AiSessionSummary {

    private String sessionId;

    /** 标题：由首轮提问截断而来 */
    private String title;

    /** 最后活跃时间（毫秒），列表按它倒序 */
    private Long lastActive;

    /** 消息条数（user + assistant 合计） */
    private Integer messageCount;
}
