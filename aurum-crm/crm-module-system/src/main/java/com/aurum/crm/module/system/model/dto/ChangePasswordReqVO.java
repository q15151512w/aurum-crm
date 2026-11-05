package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求（文档 2.7）。
 *
 * <p>已登录用户改密，身份凭证是<b>已有 Token + 原密码</b>，
 * 因此它<b>不在</b> Sa-Token 白名单里。
 *
 * <p>前端 {@code src/api/auth.js} 的注释提到「文档只给了路径没给字段」——
 * 字段名 {@code oldPassword / newPassword} 已由文档 2.7 的字段表确定，与前端一致。
 */
@Data
@Schema(description = "修改密码")
public class ChangePasswordReqVO {

    @Schema(description = "原密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入原密码")
    private String oldPassword;

    @Schema(description = "新密码，6-20 位", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 20, message = "密码长度为 6 到 20 个字符")
    private String newPassword;
}
