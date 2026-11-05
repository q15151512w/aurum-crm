package com.aurum.crm.module.bpm.mapper;

import com.aurum.crm.module.bpm.model.SysApprovalInstanceDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/** 审批实例 Mapper。包路径受 {@code @MapperScan("com.aurum.crm.**.mapper")} 覆盖。 */
@Mapper
public interface ApprovalInstanceMapper extends BaseMapperX<SysApprovalInstanceDO> {
}
