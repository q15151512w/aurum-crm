package com.aurum.crm.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-部门关联 DO，对应表 {@code sys_role_dept}。
 *
 * <p>仅当角色的 {@code data_scope = 2}（自定义数据范围）时才有意义 ——
 * 登录时据此算出 {@code LoginUser.dataScopeDeptIds}。
 *
 * <p>⚠️ 复合主键表，同 {@link SysUserRoleDO}：不继承 {@code BaseDO}、不标 {@code @TableId}。
 */
@Data
@TableName("sys_role_dept")
public class SysRoleDeptDO {

    /** 角色 ID */
    private Long roleId;

    /** 部门 ID */
    private Long deptId;
}
