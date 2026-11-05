package com.aurum.crm.module.crm.mapper;

import com.aurum.crm.module.crm.model.ProductDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品 Mapper，对应表 {@code crm_product}。
 *
 * <p>包路径在 {@code com.aurum.crm.**.mapper} 下，由 {@code MybatisPlusConfig}
 * 的 {@code @MapperScan} 自动注册，无需额外配置。
 */
@Mapper
public interface ProductMapper extends BaseMapperX<ProductDO> {
}
