package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 邮箱验证码登录请求（文档 2.3）。
 *
 * <p>与密码登录（{@link LoginReqVO}）的区别只在「怎么证明你是你」：
 * 这里用邮箱收到的验证码，密码登录用账号 + 密码 + 算术验证码。
 * 后端从 {@code doLogin} 往后完全共用同一条路径。
 */
@Data
@Schema(description = "邮箱验证码登录")
public class EmailLoginReqVO {

    @Schema(description = "注册时预留的邮箱", requiredMode = Schema.RequiredMode.REQUIRED, example = "zhangsan@qq.com")
    @NotBlank(message = "请输入邮箱")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
    private String email;

    @Schema(description = "6 位验证码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入验证码")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String emailCode;
}
