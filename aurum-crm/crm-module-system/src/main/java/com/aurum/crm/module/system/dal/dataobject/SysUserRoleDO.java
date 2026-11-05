package com.aurum.crm.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户-角色关联 DO，对应表 {@code sys_user_role}。
 *
 * <p>⚠️ 这是一张<b>复合主键</b>表（{@code (user_id, role_id)}），因此：
 * <ul>
 *     <li><b>不继承 {@code BaseDO}</b> —— 表里没有 create/update/del_flag 这些审计字段</li>
 *     <li><b>不标 {@code @TableId}</b> —— MyBatis-Plus 只认单列主键，
 *         标了会把其中一列当主键，{@code deleteById} / {@code updateById} 会删错行或直接报错</li>
 * </ul>
 * 只用 {@code selectList(...)} 查询、{@code insert(...)} / {@code delete(wrapper)} 写。
 */
@Data
@TableName("sys_user_role")
public class SysUserRoleDO {

    /** 用户 ID */
    private Long userId;

    /** 角色 ID */
    private Long roleId;
}
