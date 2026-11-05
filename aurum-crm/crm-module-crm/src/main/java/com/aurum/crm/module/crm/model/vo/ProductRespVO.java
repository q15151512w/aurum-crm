package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产品响应（列表 / 详情 / 选择器共用同一套字段名）。
 *
 * <p><b>字段名对齐前端</b>：与 {@code crm-ui} 产品页的表格列、表单字段、选择器列一致，
 * 因此前端不需要任何字段映射层（前端 `useTable` 还要求每行必须有 {@code id}）。
 * 对照表见 {@code docs/07-产品模块字段契约.md}。
 *
 * <p>{@code categoryName} / {@code unitName} / {@code creator} 不是产品表字段，
 * 由 Service 跨表批量补值（分类、计量单位、创建人），避免前端逐行再发请求。
 */
@Data
@Schema(description = "产品响应")
public class ProductRespVO {

    @Schema(description = "产品ID")
    private Long id;

    @Schema(description = "产品名称")
    private String name;

    @Schema(description = "产品编码（对应 crm_product.product_no）")
    private String code;

    @Schema(description = "产品类型ID")
    private Long categoryId;

    @Schema(description = "产品类型名称（跨表补值）")
    private String categoryName;

    @Schema(description = "计量单位ID")
    private Long unitId;

    @Schema(description = "计量单位名称（跨表补值）")
    private String unitName;

    @Schema(description = "建议销售价")
    private BigDecimal salePrice;

    @Schema(description = "成本价。⚠️ 非授权角色应返回 null，见 maskCostPriceIfNeeded")
    private BigDecimal costPrice;

    @Schema(description = "税率(%)")
    private BigDecimal taxRate;

    @Schema(description = "是否上架销售（由 status 转换）")
    private Boolean onShelf;

    @Schema(description = "产品图片 URL")
    private String imageUrl;

    @Schema(description = "产品描述")
    private String description;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建人姓名（跨表补值，create_by → sys_user.real_name）")
    private String creator;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
