package com.aurum.crm.module.system.dal.mysql;

import com.aurum.crm.mybatis.core.BaseMapperX;
import com.aurum.crm.module.system.dal.dataobject.SysDeptDO;
import org.apache.ibatis.annotations.Mapper;

/** 部门 Mapper（空接口，约定见 {@link SysUserMapper}） */
@Mapper
public interface SysDeptMapper extends BaseMapperX<SysDeptDO> {
}
