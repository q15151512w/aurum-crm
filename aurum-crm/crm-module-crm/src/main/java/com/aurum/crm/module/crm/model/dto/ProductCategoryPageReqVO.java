package com.aurum.crm.module.crm.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品类型列表查询请求（{@code GET /crm/product-category/tree}）。
 *
 * <p>前端产品类型页没有搜索框，但产品表单的「选择产品类型」选择器有搜索
 * （占位「类型名称 / 类型编号」），所以这里只需要 {@code keyword}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "产品类型分页查询")
public class ProductCategoryPageReqVO extends PageParam {

    @Schema(description = "关键字：匹配类型名称；纯数字时同时匹配类型编号（即分类ID）")
    private String keyword;
}
