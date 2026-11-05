package com.aurum.crm.module.bpm.mapper;

import com.aurum.crm.module.bpm.model.SysApprovalNodeDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/** 审批流节点 Mapper。包路径受 {@code @MapperScan("com.aurum.crm.**.mapper")} 覆盖。 */
@Mapper
public interface ApprovalNodeMapper extends BaseMapperX<SysApprovalNodeDO> {
}
