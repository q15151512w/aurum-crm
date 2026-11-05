package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 产品选择器响应（{@code GET /crm/product/simple-list}）。
 *
 * <p>字段与前端「添加产品」选择器完全一致：
 * `selectorRegistry.js` 的列读 {@code name/code/categoryName/unitName/salePrice/costPrice}，
 * `ProductItemEditor.vue` 再把选中行映射成明细行
 * （{@code productId: r.id, productName: r.name, unitName: r.unitName, unitPrice: r.salePrice}）。
 *
 * <p>因此这里**不再**返回早期版本里的 `extra` / `unitCode` / `unitDecimalPlaces`
 * —— 没有任何消费者，属于该删的冗余字段。
 */
@Data
@Schema(description = "产品选择器响应")
public class ProductSimpleRespVO {

    @Schema(description = "产品ID", example = "1")
    private Long id;

    @Schema(description = "产品名称", example = "手机")
    private String name;

    @Schema(description = "产品编码", example = "CP0001")
    private String code;

    @Schema(description = "产品类型ID")
    private Long categoryId;

    @Schema(description = "产品类型名称")
    private String categoryName;

    @Schema(description = "计量单位ID")
    private Long unitId;

    @Schema(description = "计量单位名称")
    private String unitName;

    @Schema(description = "建议销售价（写入明细行的 unit_price 快照）")
    private BigDecimal salePrice;

    @Schema(description = "成本价")
    private BigDecimal costPrice;

    @Schema(description = "税率(%)（写入明细行的 tax_rate 快照）")
    private BigDecimal taxRate;
}
