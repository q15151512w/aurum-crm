package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色组列表项。
 *
 * <p>⚠️ 展示口径：{@code name}（← {@code group_name}）、
 * {@code roleCount} 是**实时统计**的组内角色数，不是表里的列。
 */
@Data
@Schema(description = "角色组")
public class RoleGroupRespVO {

    @Schema(description = "角色组ID")
    private Long id;

    /** 角色组名称 ← sys_role_group.group_name */
    @Schema(description = "角色组名称")
    private String name;

    /** 实时统计：该组下有多少个角色（按 sys_role.group_id 统计） */
    @Schema(description = "组内角色数")
    private Integer roleCount;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
