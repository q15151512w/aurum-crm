package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 单据明细行响应体 —— **报价单 / 商机（以及将来的合同）共用**，
 * 所以类名不再带模块前缀（《02-建表文档》3.5：「三张表共用一个明细行结构」）。
 *
 * <p>字段名对齐前端 {@code components/form/ProductItemEditor.vue} 真正读写的那几个：
 * {@code productId / productName / unitName / quantity / unitPrice / discountRate / taxRate /
 * amount / remark}（外加前端到处都要的 {@code id}）。
 *
 * <p><b>{@code unitName} 必须有值</b>：编辑器的「单位」是 {@code el-select}，绑定的是
 * {@code row.unitName}。如果这里不回显单位名，用户一编辑就会看到空的单位下拉，
 * 保存时提交空串 → 明细行的 {@code unit_id} 被清成 null。
 */
@Data
@Schema(description = "单据明细行（报价单 / 商机共用）")
public class DocumentItemRespVO {

    @Schema(description = "明细ID")
    private Long id;

    @Schema(description = "产品ID（可空）")
    private Long productId;

    @Schema(description = "产品名称（快照）")
    private String productName;

    @Schema(description = "产品分类ID（快照，对应 product_type_id）")
    private Long productTypeId;

    @Schema(description = "计量单位ID")
    private Long unitId;

    @Schema(description = "计量单位名称（跨表补值，编辑器的单位下拉靠它回填）")
    private String unitName;

    @Schema(description = "数量")
    private BigDecimal quantity;

    @Schema(description = "单价（含税）")
    private BigDecimal unitPrice;

    @Schema(description = "折扣率(%)")
    private BigDecimal discountRate;

    @Schema(description = "税率(%)")
    private BigDecimal taxRate;

    @Schema(description = "行金额（含税）")
    private BigDecimal amount;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;
}
