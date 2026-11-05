package com.aurum.crm.module.system.dal.mysql;

import com.aurum.crm.mybatis.core.BaseMapperX;
import com.aurum.crm.module.system.dal.dataobject.SysRoleMenuDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-菜单关联 Mapper（空接口，约定见 {@link SysUserMapper}）。
 *
 * <p>复合主键，写入约定见 {@link SysUserRoleMapper}。
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapperX<SysRoleMenuDO> {
}
