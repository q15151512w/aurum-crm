package com.aurum.crm.module.crm.mapper;


import com.aurum.crm.module.crm.model.CustomerDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户 Mapper。
 *
 * <p>继承 {@link BaseMapperX}，包路径必须在本包下
 * （{@code com.aurum.crm.**.mapper}），由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan} 扫描注册。
 */
@Mapper
public interface CustomerMapper extends BaseMapperX<CustomerDO> {
}
