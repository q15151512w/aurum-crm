package com.aurum.crm.module.crm.ai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 对话请求。
 *
 * <p><b>历史由服务端保存（Redis），前端不再上传</b>：请求里只有一个
 * {@code sessionId} 和本轮提问。这比之前的「前端把完整历史传上来」好两处：
 * <ul>
 *   <li>上下文是**不可伪造**的 —— 以前客户端能塞任意 assistant 消息（甚至伪造
 *       system 角色）进去影响模型判断，现在历史只可能来自服务端自己写下的内容；</li>
 *   <li>请求体恒定大小，不会随着对话变长而膨胀。</li>
 * </ul>
 */
@Data
@Schema(description = "AI 对话请求")
public class AiChatReqVO {

    /**
     * 会话 ID。
     *
     * <p><b>首次提问时传 null</b>：服务端会新建一个会话，并通过 SSE 的 {@code meta}
     * 事件把新的 sessionId 回传给前端，之后前端带上它继续对话。
     *
     * <p>传了一个**已过期或不存在**的 id 时不会报错，而是同样新建一个会话 ——
     * 用户闲置太久后回来接着问，报错打断他没有任何意义。
     */
    @Schema(description = "会话ID；首次对话不传，由服务端创建并通过 meta 事件返回")
    private String sessionId;

    /** 本轮提问。 */
    @Schema(description = "用户本轮提问", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入内容")
    @Size(max = 4000, message = "提问内容过长")
    private String content;
}
