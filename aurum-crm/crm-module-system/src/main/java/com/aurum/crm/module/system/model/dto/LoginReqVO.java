package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 密码登录请求（文档 2.1）。
 *
 * <p>注意这里<b>不做长度校验</b>：登录只是拿已有密码去比对，
 * 密码策略是注册/改密时的事。对登录加长度限制只会让历史账号登不进来。
 */
@Data
@Schema(description = "密码登录")
public class LoginReqVO {

    @Schema(description = "登录账号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入账号")
    private String username;

    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入密码")
    private String password;

    /**
     * 验证码的 key，来自 {@code GET /auth/captcha}。
     *
     * <p>⚠️ 这两个字段<b>故意不加 {@code @NotBlank}</b>：
     * 验证码开关关闭时前端根本不会传它们，加了注解会被参数校验拦成 400 ——
     * 那就把一个「可关闭的功能」变成了「关不掉的功能」。
     * 「是否必填」取决于运行时开关，属于业务规则，由
     * {@code CaptchaService.verifyAndConsume} 判断。
     */
    @Schema(description = "验证码 key（来自 GET /auth/captcha，开关关闭时不传）")
    private String captchaKey;

    /**
     * 计算结果。
     *
     * <p>是数字字符串（如 {@code "8"}），可能是 {@code 0}（如 {@code 5 - 5 = ?}）。
     */
    @Schema(description = "算术题的计算结果（开关关闭时不传）")
    private String captchaCode;
}
