package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色列表 / 详情响应。
 *
 * <p>⚠️ <b>展示口径</b>（与 {@code UserRespVO} 同理）：
 * <pre>
 *   name       ← sys_role.role_name
 *   dataScope  ← sys_role.data_scope 的中文（"全部数据"/"本部门及以下"/…），不是数字码值
 *   userCount  ← 该角色下的用户数（实时统计，不是列）
 * </pre>
 *
 * <p>「选择角色」下拉用的就是这个列表接口（不是 simple-list），
 * 所以 {@code name} / {@code roleKey} / {@code dataScope} 三个字段一个都不能少。
 */
@Data
@Schema(description = "角色信息")
public class RoleRespVO {

    @Schema(description = "角色ID")
    private Long id;

    /** 角色名称 ← sys_role.role_name */
    @Schema(description = "角色名称")
    private String name;

    @Schema(description = "角色标识（role_key）")
    private String roleKey;

    /** 中文，由 data_scope 码值转换而来 */
    @Schema(description = "数据范围（中文）：全部数据/自定义/本部门/本部门及以下/仅本人")
    private String dataScope;

    /** 实时统计的用户数（前端「人数」列） */
    @Schema(description = "该角色下的用户数")
    private Integer userCount;

    /** 所属角色组 ID（null = 未分组），供编辑时回填下拉 */
    @Schema(description = "所属角色组ID")
    private Long groupId;

    /** 所属角色组名称，由 Service 跨表补 —— 角色列表的「角色组」列显示它 */
    @Schema(description = "所属角色组名称（未分组时为空串）")
    private String groupName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
