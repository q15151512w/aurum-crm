package com.aurum.crm.module.bpm.mapper;

import com.aurum.crm.module.bpm.model.BpmUserDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/** 系统用户精简投影 Mapper（审批人解析用）。 */
@Mapper
public interface BpmUserMapper extends BaseMapperX<BpmUserDO> {
}
