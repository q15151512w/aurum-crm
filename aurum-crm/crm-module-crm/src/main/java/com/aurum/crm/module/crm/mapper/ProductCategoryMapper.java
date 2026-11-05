package com.aurum.crm.module.crm.mapper;

import com.aurum.crm.module.crm.model.ProductCategoryDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品分类 Mapper，对应表 {@code crm_product_category}。
 */
@Mapper
public interface ProductCategoryMapper extends BaseMapperX<ProductCategoryDO> {
}
