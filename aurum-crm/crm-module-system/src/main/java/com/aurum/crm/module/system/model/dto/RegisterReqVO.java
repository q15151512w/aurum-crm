package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求（文档 2.4）。
 *
 * <p>⚠️ 注册是<b>投递申请</b>，不是开通账号：落地时 {@code status = 2}（禁用）、
 * 不写 {@code sys_user_role}。管理员在「人员管理」里分配角色并激活后才能登录。
 *
 * <p>密码长度统一为 6–20 位。文档 2.4 原文写的是 5–32 位，与 2.5 / 2.7 的
 * 6–20 位不一致，已统一修订（文档与前端同步改过）。
 *
 * <p><b>验证字段已从手机号换成邮箱</b>：原来靠手机号 + 短信验证码证明「这是你本人」，
 * 现在改为邮箱 + 邮箱验证码。手机号不再参与身份验证，只作为用户资料字段保留在
 * {@code sys_user.phone}（由管理员在人员管理里维护）。
 */
@Data
@Schema(description = "注册账号")
public class RegisterReqVO {

    @Schema(description = "登录账号，2-32 位，全局唯一", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入账号")
    @Size(min = 2, max = 32, message = "账号长度为 2 到 32 个字符")
    private String username;

    /**
     * 注册邮箱。
     *
     * <p>它同时承担两个职责：收验证码（证明邮箱可用）+ 之后用它登录 / 找回密码。
     * 所以落库时要求<b>全局唯一</b> —— 靠 {@code sys_user} 的
     * {@code uk_email (email, is_active)} 唯一索引兜底，Service 里另有友好提示。
     */
    @Schema(description = "邮箱地址（同时用于登录与找回密码）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "zhangsan@qq.com")
    @NotBlank(message = "请输入邮箱")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
    private String email;

    @Schema(description = "6 位邮箱验证码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入验证码")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String emailCode;

    @Schema(description = "密码，6-20 位", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 20, message = "密码长度为 6 到 20 个字符")
    private String password;
}
