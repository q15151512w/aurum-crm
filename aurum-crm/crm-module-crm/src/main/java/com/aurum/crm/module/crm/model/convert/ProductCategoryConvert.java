package com.aurum.crm.module.crm.model.convert;

import com.aurum.crm.module.crm.model.ProductCategoryDO;
import com.aurum.crm.module.crm.model.dto.ProductCategorySaveReqVO;
import com.aurum.crm.module.crm.model.vo.ProductCategoryRespVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 产品类型对象转换器（MapStruct）。
 *
 * <p>{@code code} 由 Service 用分类ID 派生（临时方案），{@code creator} 由 Service 跨表补名，
 * {@code ancestors} 由 Service 推导 —— 都不在这里映射。
 */
@Mapper(componentModel = "spring")
public interface ProductCategoryConvert {

    @Mapping(target = "id", source = "categoryId")
    @Mapping(target = "name", source = "categoryName")
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "creator", ignore = true)
    ProductCategoryRespVO toRespVO(ProductCategoryDO categoryDO);

    @Mapping(target = "categoryName", source = "name")
    ProductCategoryDO toDO(ProductCategorySaveReqVO reqVO);
}
