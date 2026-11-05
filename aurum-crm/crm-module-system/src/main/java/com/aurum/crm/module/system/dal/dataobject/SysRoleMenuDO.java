package com.aurum.crm.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-菜单关联 DO，对应表 {@code sys_role_menu}（RBAC 的核心）。
 *
 * <p>⚠️ 复合主键表，同 {@link SysUserRoleDO}：不继承 {@code BaseDO}、不标 {@code @TableId}。
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenuDO {

    /** 角色 ID */
    private Long roleId;

    /** 菜单 ID */
    private Long menuId;
}
