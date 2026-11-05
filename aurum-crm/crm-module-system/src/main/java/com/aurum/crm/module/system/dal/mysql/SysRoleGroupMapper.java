package com.aurum.crm.module.system.dal.mysql;

import com.aurum.crm.mybatis.core.BaseMapperX;
import com.aurum.crm.module.system.dal.dataobject.SysRoleGroupDO;
import org.apache.ibatis.annotations.Mapper;

/** 角色组 Mapper（空接口，约定见 {@link SysUserMapper}） */
@Mapper
public interface SysRoleGroupMapper extends BaseMapperX<SysRoleGroupDO> {
}
