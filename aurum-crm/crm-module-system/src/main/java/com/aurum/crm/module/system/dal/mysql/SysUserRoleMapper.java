package com.aurum.crm.module.system.dal.mysql;

import com.aurum.crm.mybatis.core.BaseMapperX;
import com.aurum.crm.module.system.dal.dataobject.SysUserRoleDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联 Mapper（空接口，约定见 {@link SysUserMapper}）。
 *
 * <p>⚠️ {@code SysUserRoleDO} 是复合主键，只能用 {@code selectList(wrapper)} /
 * {@code insert(...)} / {@code delete(wrapper)}，<b>不要</b>用 {@code deleteById} / {@code updateById}。
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapperX<SysUserRoleDO> {
}
