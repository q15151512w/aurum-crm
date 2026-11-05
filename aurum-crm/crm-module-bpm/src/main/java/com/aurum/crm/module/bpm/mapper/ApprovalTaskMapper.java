package com.aurum.crm.module.bpm.mapper;

import com.aurum.crm.module.bpm.model.SysApprovalTaskDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/** 审批任务 Mapper。包路径受 {@code @MapperScan("com.aurum.crm.**.mapper")} 覆盖。 */
@Mapper
public interface ApprovalTaskMapper extends BaseMapperX<SysApprovalTaskDO> {
}
