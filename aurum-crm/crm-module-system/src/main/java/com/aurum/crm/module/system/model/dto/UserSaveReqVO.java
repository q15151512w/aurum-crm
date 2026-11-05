package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 人员新增 / 修改请求。
 *
 * <p><b>⚠️ 这里是「展示口径」</b>：前端表单用中文字符串传值
 * （{@code gender="男"}、{@code workStatus="在职"}、{@code accountStatus="已激活"}），
 * 由 {@code DisplayEnumTranslator} 在后端转换成库里的 TINYINT 码值。
 * 别按 DDL 的列名去改这些字段（如把 {@code name} 改成 {@code realName}）——
 * 前端就是按 {@code name} 发的。
 *
 * <p><b>⚠️ {@code roleId} 与 {@code roleIds} 都要收</b>：
 * 接口文档 3.1 写的是 `roleIds`（多选），但前端表单实际发的是 `roleId`（单选，
 * 由选择器控件的 extraProp 写回）。两个字段都接，取非空的那个 —— 详见 Service。
 */
@Data
@Schema(description = "人员新增/修改")
public class UserSaveReqVO {

    @Schema(description = "登录账号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入登录账号")
    private String username;

    /**
     * 登录密码。**新增必填、修改时留空表示不改**。
     *
     * <p>不能加 {@code @NotBlank} —— 修改时前端确实会传空字符串。
     * 这个「新增必填、编辑可空」的规则由 Service 按 id 是否存在来判断。
     */
    @Schema(description = "登录密码（新增必填；修改留空表示不改）")
    private String password;

    @Schema(description = "姓名")
    @NotBlank(message = "请输入姓名")
    private String name;

    /** 中文：男 / 女 */
    @Schema(description = "性别：男 / 女")
    private String gender;

    @Schema(description = "手机号码")
    private String phone;

    @Schema(description = "联系邮箱")
    private String email;

    @Schema(description = "所属部门ID")
    private Long deptId;

    /** 部门名称，仅用于列表展示；前端会一起发过来，后端忽略（名称以库里的 sys_dept 为准） */
    @Schema(description = "所属部门名称（仅展示用，后端忽略）")
    private String deptName;

    /** 角色 ID。前端表单实际发的字段（单选） */
    @Schema(description = "角色ID（前端表单实际发送）")
    private Long roleId;

    /** 角色 ID 列表。接口文档 3.1 的字段（多选）。与 roleId 二选一 */
    @Schema(description = "角色ID列表（接口文档口径，多选）")
    private List<Long> roleIds;

    /** 中文：在职 / 离职 */
    @Schema(description = "在职状态：在职 / 离职")
    private String workStatus;

    /** 中文：已激活 / 已禁用 */
    @Schema(description = "账号状态：已激活 / 已禁用")
    private String accountStatus;

    @Schema(description = "备注")
    private String remark;
}
