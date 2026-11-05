package com.aurum.crm.module.system.dal.dataobject;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色组 DO，对应表 {@code sys_role_group}。
 *
 * <p><b>定位：纯分组标签，不参与任何权限判定。</b>
 * 权限只看 {@code sys_role.role_key} + {@code data_scope} + {@code sys_role_menu}；
 * 角色组唯一的作用是让角色列表能按「组」归类展示。
 *
 * <p>同样<b>不要</b>声明 {@code isActive}（生成列，写进去会报错）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_group")
public class SysRoleGroupDO extends BaseDO {

    /** 角色组 ID */
    @TableId
    private Long groupId;

    /** 角色组名称（存活行内唯一） */
    private String groupName;

    /** 显示排序 */
    private Integer sort;

    /** 备注 */
    private String remark;
}
