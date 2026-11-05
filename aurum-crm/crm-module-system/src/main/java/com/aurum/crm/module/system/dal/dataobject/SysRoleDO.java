package com.aurum.crm.module.system.dal.dataobject;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色 DO，对应表 {@code sys_role}。
 *
 * <p>同样<b>不要</b>声明 {@code isActive}（生成列，同 {@link SysUserDO} 的说明）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRoleDO extends BaseDO {

    /** 角色 ID */
    @TableId
    private Long roleId;

    /** 角色名称，如「销售专员」 */
    private String roleName;

    /** 角色标识，如 {@code admin} / {@code sales} —— 登录时写进 Session 的就是它 */
    private String roleKey;

    /** 数据范围：1 全部 / 2 自定义 / 3 本部门 / 4 本部门及以下 / 5 仅本人 */
    private Integer dataScope;

    /**
     * 所属角色组 ID，NULL = 未分组。
     *
     * <p>⚠️ 角色组只是<b>分组标签</b>，不参与权限判定 ——
     * 删组时要把成员的 {@code group_id} 置回 NULL（见 {@code RoleGroupServiceImpl.delete}），
     * 否则角色表里会留下指向已删组的孤儿引用，列表按组归类时会错乱。
     */
    private Long groupId;

    /** 显示排序 */
    private Integer sort;

    /** 状态：0 停用，1 正常 */
    private Integer status;

    /** 备注 */
    private String remark;
}
