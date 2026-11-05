package com.aurum.crm.module.crm.mapper;

import com.aurum.crm.module.crm.model.CustomerMiniDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户精简投影 Mapper，用于商机列表 / 选择器的「客户」列补名与新建商机时的客户存在性校验。
 *
 * <p>与 {@code SysUserMiniMapper} 同一定位：只补名，不进入客户模块的 CRUD 链路。
 */
@Mapper
public interface CustomerMiniMapper extends BaseMapperX<CustomerMiniDO> {
}
