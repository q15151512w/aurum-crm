package com.aurum.crm.module.system.dal.mysql;

import com.aurum.crm.mybatis.core.BaseMapperX;
import com.aurum.crm.module.system.dal.dataobject.SysMenuDO;
import org.apache.ibatis.annotations.Mapper;

/** 菜单 Mapper（空接口，约定见 {@link SysUserMapper}） */
@Mapper
public interface SysMenuMapper extends BaseMapperX<SysMenuDO> {
}
