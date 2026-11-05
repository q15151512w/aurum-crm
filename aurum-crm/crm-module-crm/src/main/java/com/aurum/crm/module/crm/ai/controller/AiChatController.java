package com.aurum.crm.module.crm.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.module.crm.ai.model.vo.AiChatReqVO;
import com.aurum.crm.module.crm.ai.service.AiChatService;
import com.aurum.crm.module.crm.ai.session.AiSessionSummary;
import com.aurum.crm.module.crm.ai.session.AiStoredMessage;
import com.aurum.crm.module.system.core.log.Log;
import com.aurum.crm.module.system.model.OperationBusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 小助手接口。
 *
 * <p><b>权限点必须写三段式 {@code ai:chat:use}</b>：超管的 {@code *:*:*} 在 Sa-Token 里
 * 只是普通通配模式，模式中的 {@code :} 是字面量，只能命中三段式标识。
 * 写成两段式（{@code ai:chat}）会让超管反而调不通 —— 这正是 BUG-003 的成因。
 */
@Tag(name = "AI 小助手")
@RestController
@RequestMapping("/ai")
@Validated
public class AiChatController {

    @Resource
    private AiChatService aiChatService;

    /**
     * 流式对话。
     *
     * <p><b>返回类型是 {@code void}</b>：响应体由 {@link AiChatService} 直接写
     * （手工 SSE 帧），不让 Spring 的消息转换器插手 —— 否则它会试图把返回值
     * 序列化成 JSON 追加到已经写了 {@code text/event-stream} 的响应里。
     *
     * <p>权限与参数校验都在**进入方法体之前**完成（{@code SaInterceptor} 处理
     * {@code @SaCheckPermission}，{@code @Valid} 处理请求体），所以这两类失败
     * 会由 {@code GlobalExceptionHandler} 返回**普通 JSON**，前端不会进入 SSE 解析分支。
     * 只有真正开始对话之后发生的错误才走 {@code event: error}。
     */
    @Operation(summary = "AI 对话（SSE 流式）")
    @Log(title = "AI小助手", businessType = OperationBusinessType.QUERY)
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckPermission("ai:chat:use")
    public void chatStream(@Valid @RequestBody AiChatReqVO reqVO, HttpServletResponse response) {
        aiChatService.streamChat(reqVO, response);
    }

    /**
     * 我的会话列表。
     *
     * <p>不返回消息正文 —— 会话多了以后那是几 MB 的无用载荷，列表只需要标题和时间。
     */
    @Operation(summary = "我的会话列表")
    @GetMapping("/sessions")
    @SaCheckPermission("ai:chat:use")
    public CommonResult<List<AiSessionSummary>> sessions() {
        return CommonResult.success(aiChatService.listSessions());
    }

    /**
     * 某个会话的全部消息。
     *
     * <p>传别人的 sessionId 不会报错，只会返回空列表 —— 存储层的键前缀绑死当前用户，
     * 查不到不属于自己的会话（也不该给出「这个 id 存在但你没权限」这种可用于探测的反馈）。
     */
    @Operation(summary = "会话消息历史")
    @GetMapping("/sessions/{sessionId}/messages")
    @SaCheckPermission("ai:chat:use")
    public CommonResult<List<AiStoredMessage>> messages(@PathVariable("sessionId") String sessionId) {
        return CommonResult.success(aiChatService.history(sessionId));
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/sessions/{sessionId}")
    @SaCheckPermission("ai:chat:use")
    public CommonResult<Boolean> deleteSession(@PathVariable("sessionId") String sessionId) {
        aiChatService.deleteSession(sessionId);
        return CommonResult.success(true);
    }
}
