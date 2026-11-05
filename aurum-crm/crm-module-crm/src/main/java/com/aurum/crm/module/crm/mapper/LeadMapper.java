package com.aurum.crm.module.crm.mapper;

import com.aurum.crm.module.crm.model.LeadDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 线索 Mapper。
 *
 * <p>继承 {@link BaseMapperX}，包路径必须在本包下（{@code com.aurum.crm.**.mapper}），
 * 由 {@code MybatisPlusConfig} 的 {@code @MapperScan} 扫描注册。
 */
@Mapper
public interface LeadMapper extends BaseMapperX<LeadDO> {
}
