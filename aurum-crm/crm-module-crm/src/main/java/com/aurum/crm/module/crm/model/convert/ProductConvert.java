package com.aurum.crm.module.crm.model.convert;

import com.aurum.crm.module.crm.model.ProductDO;
import com.aurum.crm.module.crm.model.dto.ProductSaveReqVO;
import com.aurum.crm.module.crm.model.vo.ProductRespVO;
import com.aurum.crm.module.crm.model.vo.ProductSimpleRespVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * 产品对象转换器（MapStruct，编译期生成实现）。
 *
 * <p><b>为什么有这么多 {@code @Mapping}</b>：DO 的字段名必须跟数据库列一一对应
 * （{@code product_name} / {@code product_no} / {@code price} / {@code status} / {@code image}），
 * 而对外字段名要跟前端一致（{@code name} / {@code code} / {@code salePrice} / {@code onShelf} / {@code imageUrl}）。
 * 两边名字不同，所以在这里显式建立对应关系 —— 只在这一处做映射，Service 层就全用前端字段名。
 *
 * <p>{@code categoryName} / {@code unitName} / {@code creator} 在 DO 中没有对应字段，
 * 由 Service 跨表批量补值，这里一律 {@code ignore}。
 */
@Mapper(componentModel = "spring")
public interface ProductConvert {

    @Mapping(target = "id", source = "productId")
    @Mapping(target = "name", source = "productName")
    @Mapping(target = "code", source = "productNo")
    @Mapping(target = "salePrice", source = "price")
    @Mapping(target = "imageUrl", source = "image")
    @Mapping(target = "onShelf", source = "status", qualifiedByName = "statusToOnShelf")
    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "unitName", ignore = true)
    @Mapping(target = "creator", ignore = true)
    ProductRespVO toRespVO(ProductDO productDO);

    /**
     * 请求 → DO。
     *
     * <p>{@code unitId} 会由 Service 按 {@code unitName} 解析后覆盖；
     * {@code productNo} 同理（前端传了就采纳，没传才生成）。
     */
    @Mapping(target = "productId", ignore = true)
    @Mapping(target = "productName", source = "name")
    @Mapping(target = "productNo", source = "code")
    @Mapping(target = "price", source = "salePrice")
    @Mapping(target = "image", source = "imageUrl")
    @Mapping(target = "status", source = "onShelf", qualifiedByName = "onShelfToStatus")
    ProductDO toDO(ProductSaveReqVO reqVO);

    @Mapping(target = "id", source = "productId")
    @Mapping(target = "name", source = "productName")
    @Mapping(target = "code", source = "productNo")
    @Mapping(target = "salePrice", source = "price")
    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "unitName", ignore = true)
    ProductSimpleRespVO toSimpleRespVO(ProductDO productDO);

    /** 状态码 → 布尔：只有 1 算上架 */
    @Named("statusToOnShelf")
    default Boolean statusToOnShelf(Integer status) {
        return status != null && status == 1;
    }

    /**
     * 布尔 → 状态码。
     *
     * <p>⚠️ {@code null} 必须原样返回 {@code null}：全局 {@code update-strategy: not_null}
     * 下，null 表示"不改这一列"。前端编辑表单若没带 {@code onShelf}，
     * 就不能把产品偷偷改成下架（0）。
     */
    @Named("onShelfToStatus")
    default Integer onShelfToStatus(Boolean onShelf) {
        return onShelf == null ? null : (onShelf ? 1 : 0);
    }
}
