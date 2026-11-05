package com.aurum.crm.module.crm.ai.service;

import com.aurum.crm.module.crm.ai.model.vo.AiChatReqVO;
import com.aurum.crm.module.crm.ai.session.AiSessionSummary;
import com.aurum.crm.module.crm.ai.session.AiStoredMessage;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * AI 对话编排。
 *
 * <p>会话历史由服务端保存在 Redis（见 {@code AiChatSessionStore}），
 * 前端只持有 sessionId 与当前视图，不再上传历史 —— 所以下面这几个方法
 * **都不接收 userId**：一律从 {@code LoginUserHolder} 取当前登录人，
 * 调用方无法越权指定读谁的会话。
 */
public interface AiChatService {

    /**
     * 流式对话：把回答边生成边写进 {@code response}，方法返回时流已结束。
     *
     * <p><b>实现必须全程同步、跑在请求线程上。</b> 原因见实现类注释 ——
     * 简言之：工具查询依赖 {@code LoginUserHolder}（ThreadLocal）来触发数据权限过滤，
     * 一旦切到别的线程，过滤条件会静默消失，用户就能看到不属于他的数据。
     *
     * @param reqVO    会话 ID（首次为空）+ 本轮提问
     * @param response 由实现负责写入 SSE 帧；调用方不要再碰它
     */
    void streamChat(AiChatReqVO reqVO, HttpServletResponse response);

    /** 我的会话列表，按最后活跃时间倒序。 */
    List<AiSessionSummary> listSessions();

    /** 某个会话的全部消息（时间正序）。不属于自己的会话返回空列表。 */
    List<AiStoredMessage> history(String sessionId);

    /** 删掉某个会话。不属于自己的会话静默无操作。 */
    void deleteSession(String sessionId);
}
