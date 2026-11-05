package com.aurum.crm.module.bpm.mapper;

import com.aurum.crm.module.bpm.model.BpmUserRoleDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/** 用户-角色关联精简投影 Mapper（审批人类型 2 解析用）。 */
@Mapper
public interface BpmUserRoleMapper extends BaseMapperX<BpmUserRoleDO> {
}
