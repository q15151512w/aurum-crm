package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应（文档 2.1 / 2.3，两种登录方式响应完全一致）。
 *
 * <p>前端 {@code userStore.setLoginToken(data)} 读的是 {@code data?.token || data}，
 * 所以 {@code token} 字段名不能改。
 *
 * <p>{@code expiresIn} 单位是<b>秒</b>。取值来自 Sa-Token 的
 * {@code getTokenInfo().getTokenTimeout()}，不要硬编码 86400 ——
 * {@code application.yml} 里配了 {@code active-timeout: 1800}（30 分钟无操作失效），
 * 实际返回值受它影响。
 */
@Data
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginRespVO {

    @Schema(description = "访问令牌，前端存 localStorage 后以 Authorization: Bearer <token> 发送")
    private String token;

    @Schema(description = "有效期（秒）")
    private Long expiresIn;
}
