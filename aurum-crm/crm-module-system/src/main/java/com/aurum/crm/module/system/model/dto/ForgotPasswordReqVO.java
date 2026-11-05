package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 忘记密码请求（文档 2.5）。
 *
 * <p>未登录自助重置，所以它在 Sa-Token 白名单里 —— 身份凭证是<b>邮箱验证码</b>，
 * 不是 Token。
 */
@Data
@Schema(description = "忘记密码")
public class ForgotPasswordReqVO {

    @Schema(description = "注册时预留的邮箱", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "zhangsan@qq.com")
    @NotBlank(message = "请输入邮箱")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
    private String email;

    @Schema(description = "6 位邮箱验证码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入验证码")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String emailCode;

    @Schema(description = "新密码，6-20 位", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 20, message = "密码长度为 6 到 20 个字符")
    private String newPassword;
}
