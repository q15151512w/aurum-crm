package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 保存角色的菜单权限，对应 {@code PUT /system/role/{id}/permissions}。
 *
 * <p>⚠️ <b>提交的是对象，查询返回的却是裸数组</b> —— 两个方向结构不对称：
 * <pre>
 *   GET  /system/role/{id}/permissions  →  [1, 2, 3]        （纯数组）
 *   PUT  /system/role/{id}/permissions  ←  { "menuIds": [..] } （对象）
 * </pre>
 * 前端就是这么写的（{@code getRolePermissions} / {@code saveRolePermissions}），
 * 别「顺手统一」成一样 —— 那会把前端改坏。
 */
@Data
@Schema(description = "保存角色菜单权限")
public class RolePermissionReqVO {

    /**
     * 菜单 ID 集合。
     *
     * <p>前端提交的是「勾选节点 + 半选节点」的并集（`[...getCheckedKeys(), ...getHalfCheckedKeys()]`）——
     * 半选节点也要存，否则只勾子菜单时父目录会变成孤儿，菜单树拼不出来。
     */
    @Schema(description = "菜单ID列表（含父节点的半选）")
    private List<Long> menuIds;
}
