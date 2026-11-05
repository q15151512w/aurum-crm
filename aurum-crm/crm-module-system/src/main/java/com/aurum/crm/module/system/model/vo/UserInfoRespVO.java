package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 当前登录用户信息（文档 2.6 的 {@code GET /auth/me}）。
 *
 * <p>这是前端动态菜单的唯一数据源：路由守卫在 {@code dynamicRoutes} 为空时调它，
 * 拿到 {@code menus} 后生成路由与侧边栏，拿 {@code roles} / {@code permissions}
 * 供权限判断。
 *
 * <p><b>为什么「个人资料」那几个字段也在这里</b>（{@code nickname / phone / email / avatar}）：
 * 顶栏「个人信息」弹窗改的就是这几项，而 {@code PUT /auth/profile} 是
 * {@code update-strategy: not_null} 的语义 —— <b>不传 = 不改</b>。
 * 回显不到当前值的话，用户只改个姓名就会把手机号、邮箱、头像一起提交成 null。
 * 与其为读再开一个 {@code GET /auth/profile}，不如让 {@code /auth/me} 一次给全：
 * 前端 {@code userStore.userInfo} 本来就存着，弹窗打开时零额外请求。
 *
 * <p>⚠️ {@code sys_user.phone} 是<b>明文存储</b>的（只有登录日志的展示口径
 * {@link LoginLogRespVO#getPhone()} 做了脱敏）。《05-接口文档》2.8 里
 * 「手机号（加密存储）」的说法与代码不符，别照着它去实现。
 */
@Data
@Schema(description = "当前登录用户信息")
public class UserInfoRespVO {

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "真实姓名（顶栏显示用）")
    private String realName;

    @Schema(description = "所属部门 ID")
    private Long deptId;

    @Schema(description = "所属部门名称（顶栏下拉显示用）")
    private String deptName;

    // ------------------------------------------------------------------------
    //  以下 5 项供前端「个人信息」页（views/profile/index.vue）展示。
    //
    //  ⚠️ 其中前 4 项与 ProfileUpdateReqVO 的可改字段一一对应 —— 那边能改的就这些。
    //     gender **只能看不能改**：改性别属于「人员管理」（/system/user），
    //     要 system:user:update 权限，ProfileUpdateReqVO 里刻意没有这个字段。
    // ------------------------------------------------------------------------

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "手机号码")
    private String phone;

    @Schema(description = "联系邮箱")
    private String email;

    @Schema(description = "头像地址")
    private String avatar;

    /**
     * 性别，<b>中文展示口径</b>（男 / 女 / 未知）。
     *
     * <p>⚠️ 库里 {@code sys_user.gender} 是 {@code TINYINT}（0 未知 / 1 男 / 2 女），
     * 这里跟着 {@code /system/user} 的 {@link UserRespVO#getGender()} 一起用中文 ——
     * 前端两个页面显示的是同一个人的同一件事，口径不该有两套。
     *
     * <p>因为要转码，{@code AuthConvert} 里这个字段必须 {@code ignore} 掉，
     * 由 Service 层调 {@code DisplayEnumTranslator.genderToText()} 填。
     * 不 ignore 的话 MapStruct 会自己把 Integer 映射成 String，得到的是 "1" / "2"。
     */
    @Schema(description = "性别：男 / 女 / 未知")
    private String gender;

    @Schema(description = "角色标识集合，如 [\"admin\"] / [\"sales\"]")
    private List<String> roles;

    /**
     * 权限标识集合。
     *
     * <p>超管返回 {@code ["*:*:*"]} —— 这个值<b>不在数据库里</b>，由后端合成。
     */
    @Schema(description = "权限标识集合，超管为 [\"*:*:*\"]")
    private List<String> permissions;

    @Schema(description = "菜单树（含目录、菜单与按钮，按钮由前端过滤）")
    private List<MenuRespVO> menus;
}
