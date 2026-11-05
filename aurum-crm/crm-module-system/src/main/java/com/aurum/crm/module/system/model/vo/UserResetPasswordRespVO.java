package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 重置密码的响应。
 *
 * <p>⚠️ <b>必须把新密码返回给前端</b>：前端调
 * {@code resetUserPassword(id, {})} 时**传的是空对象**，它期望后端生成新密码并下发，
 * 然后弹给管理员看（管理员再转告本人）。不返回的话，重置完之后谁都不知道新密码是什么。
 */
@Data
@AllArgsConstructor
@Schema(description = "重置密码结果")
public class UserResetPasswordRespVO {

    @Schema(description = "新密码（明文，仅此一次返回，请管理员转告本人）")
    private String password;
}
