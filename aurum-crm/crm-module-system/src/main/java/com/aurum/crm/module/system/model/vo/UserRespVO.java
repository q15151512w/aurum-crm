package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人员列表 / 详情响应。
 *
 * <p>⚠️ <b>字段名是「展示口径」，与 DDL 列名不同</b> ——
 * 前端表格直接渲染这些 prop，名字对不上就是<b>整列显示 --</b>：
 *
 * <pre>
 *   name           ← sys_user.real_name
 *   deptName       ← sys_dept.dept_name（跨表）
 *   superiorName   ← sys_user.real_name（跨表，上级的姓名）
 *   roleName       ← sys_role.role_name（跨表）
 *   workStatus     ← 由 sys_user.status 派生（中文「在职/离职」）
 *   accountStatus  ← 由 sys_user.status 派生（中文「已激活/已禁用」）
 * </pre>
 *
 * <p>这是《05-接口文档》已承认的既有现象（见 4.5 节联系人那处的说明：
 * 「前端用展示口径，DDL 是权威」），不要「纠正」成 realName / deptId 之类。
 */
@Data
@Schema(description = "人员信息")
public class UserRespVO {

    /** 用户 ID。雪花值会被 JsSafeLongSerializer 序列化成字符串 */
    @Schema(description = "用户ID")
    private Long id;

    /** 姓名 ← sys_user.real_name */
    @Schema(description = "姓名")
    private String name;

    @Schema(description = "登录账号")
    private String username;

    /** 中文：男 / 女 */
    @Schema(description = "性别：男 / 女")
    private String gender;

    @Schema(description = "手机号码")
    private String phone;

    @Schema(description = "联系邮箱")
    private String email;

    @Schema(description = "所属部门ID")
    private Long deptId;

    @Schema(description = "所属部门名称")
    private String deptName;

    @Schema(description = "直属上级姓名（未设置时为空串）")
    private String superiorName;

    @Schema(description = "角色名称")
    private String roleName;

    /** 由 status 派生：离职 / 在职 */
    @Schema(description = "在职状态：在职 / 离职")
    private String workStatus;

    /** 由 status 派生：已激活 / 已禁用 */
    @Schema(description = "账号状态：已激活 / 已禁用")
    private String accountStatus;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
