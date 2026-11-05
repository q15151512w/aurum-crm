package com.aurum.crm.module.crm.mapper;


import com.aurum.crm.module.crm.model.OwnerChangeLogDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 归属流转记录 Mapper（只追加，不更新不删除）。
 */
@Mapper
public interface OwnerChangeLogMapper extends BaseMapperX<OwnerChangeLogDO> {
}
