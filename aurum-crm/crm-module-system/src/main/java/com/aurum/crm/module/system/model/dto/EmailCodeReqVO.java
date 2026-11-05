package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送邮箱验证码请求（文档 2.2）。
 *
 * <p>前端传的是请求体 {@code { email }}，不是 query 参数。
 * 也<b>没有 scene 字段</b> —— 登录 / 注册 / 找回密码共用同一套验证码：
 * 验证码本身是通用的，区分场景反而要维护三套模板与三个 Redis key，而文案其实一样。
 */
@Data
@Schema(description = "发送邮箱验证码")
public class EmailCodeReqVO {

    /**
     * 目标邮箱。
     *
     * <p>{@code @Email} 的校验比较宽松（只挡明显不是邮箱的串），
     * 与前端保持一致即可 —— 真正能不能收到信只有发了才知道，
     * 过严的正则反而会误杀合法邮箱。长度上限对齐 {@code sys_user.email} 的 VARCHAR(128)。
     */
    @Schema(description = "邮箱地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "zhangsan@qq.com")
    @NotBlank(message = "请输入邮箱")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
    private String email;
}
