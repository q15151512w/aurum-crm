package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 修改个人资料请求（文档 2.8）。
 *
 * <p>全部字段可选，{@code null} 表示「不改」（依赖全局 {@code update-strategy: not_null}）。
 *
 * <p>⚠️ <b>这个类结构上不存在 userId 字段</b>，这是「只能改自己」的第一道保证 ——
 * 比「在 Service 里忽略请求传来的 userId」可靠得多：后者一旦有人手滑加上就破防了。
 * 用户 ID 一律从登录态取。
 *
 * <p>同理，{@code username / deptId / superiorId / status / 角色} 也不在这里，
 * 它们属于「人员管理」（{@code /system/user}），需要独立的权限。
 */
@Data
@Schema(description = "修改个人资料")
public class ProfileUpdateReqVO {

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "昵称")
    private String nickname;

    /**
     * 手机号。
     *
     * <p>{@code @Pattern} 对 {@code null} 视为通过，所以可空字段这样写是安全的。
     *
     * <p>⚠️ 但 {@code @Pattern} 对<b>空串不通过</b> —— 而「清空手机号」是个人资料页
     * 上一个合法操作（用户就是不想留手机号了）。这时前端只能传空串，因为
     * {@code update-strategy: not_null} 下 {@code null} 的语义是「不改」而不是「清空」。
     * 所以正则里显式放行空串，否则用户一点保存就得到「手机号格式不正确」，
     * 而他什么都没填错 —— 这是最难自我解释的一类报错。
     *
     * <p>（邮箱不用改：Hibernate 的 {@code @Email} 对 null 与空串都返回 true。）
     */
    @Schema(description = "手机号，传空串表示清空")
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "邮箱")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "头像地址（先调 /system/file/upload 拿到）")
    private String avatar;
}
