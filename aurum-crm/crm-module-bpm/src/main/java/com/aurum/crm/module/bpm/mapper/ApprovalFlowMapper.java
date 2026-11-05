package com.aurum.crm.module.bpm.mapper;

import com.aurum.crm.module.bpm.model.SysApprovalFlowDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/** 审批流模板 Mapper。包路径受 {@code @MapperScan("com.aurum.crm.**.mapper")} 覆盖。 */
@Mapper
public interface ApprovalFlowMapper extends BaseMapperX<SysApprovalFlowDO> {
}
