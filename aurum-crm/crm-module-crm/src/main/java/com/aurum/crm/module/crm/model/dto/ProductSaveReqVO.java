package com.aurum.crm.module.crm.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 产品新增 / 修改请求，字段名与前端的表单模型一致。
 *
 * <p>前端提交体就是它的表单对象（{@code useFormDialog} 只提交 {@code emptyForm()} 里声明过的键，
 * 并会摘掉 {@code id}），所以这里的字段名必须和 {@code product/index.vue} 的
 * {@code FIELDS}/{@code emptyForm} 一模一样。
 *
 * <p><b>几个与后端存储的对应关系</b>：
 * <ul>
 *     <li>{@code code} → {@code crm_product.product_no}：**允许前端传**（唯一冲突返回 2402），
 *         不传时才由服务端生成。前端表单里它可填，并注明"编号规则引擎就绪后改由后端生成"</li>
 *     <li>{@code unitName} → {@code unit_id}：前端下拉只给单位**名字**，
 *         服务端按名字解析（解析不到返回 400 并列出可用单位）</li>
 *     <li>{@code onShelf} → {@code status}：布尔 ↔ 0/1。<b>传 null 表示不改状态</b></li>
 * </ul>
 *
 * <p>前端还会提交 {@code categoryName}（选择器写入的显示文本）—— 这里**不声明**该字段，
 * Spring Boot 默认关闭了 {@code FAIL_ON_UNKNOWN_PROPERTIES}，多余字段会被忽略；
 * 真正生效的是选择器同时写入的 {@code categoryId}。
 */
@Data
@Schema(description = "产品新增/修改")
public class ProductSaveReqVO {

    @Schema(description = "产品名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "手机")
    @NotBlank(message = "产品名称不能为空")
    @Size(max = 128, message = "产品名称长度不能超过 128")
    private String name;

    @Schema(description = "产品编码，不传则服务端生成（CP+雪花ID）", example = "CP0001")
    @Size(max = 64, message = "产品编码长度不能超过 64")
    private String code;

    @Schema(description = "产品类型ID（选择器写入）")
    private Long categoryId;

    @Schema(description = "计量单位名称（前端下拉给的是名字，服务端解析成 unitId）", example = "台")
    @Size(max = 32, message = "计量单位名称长度不能超过 32")
    private String unitName;

    @Schema(description = "计量单位ID（传了则优先用它，否则按 unitName 解析）")
    private Long unitId;

    @Schema(description = "建议销售价，缺省 0", example = "199.00")
    @DecimalMin(value = "0", message = "建议销售价不能为负数")
    @Digits(integer = 13, fraction = 2, message = "建议销售价最多 13 位整数、2 位小数")
    private BigDecimal salePrice;

    @Schema(description = "成本价，缺省 0", example = "80.00")
    @DecimalMin(value = "0", message = "成本价不能为负数")
    @Digits(integer = 13, fraction = 2, message = "成本价最多 13 位整数、2 位小数")
    private BigDecimal costPrice;

    @Schema(description = "税率(%)，缺省 0（前端表单暂未开放该项）", example = "13.00")
    @DecimalMin(value = "0", message = "税率不能为负数")
    @DecimalMax(value = "100", message = "税率不能超过 100")
    @Digits(integer = 3, fraction = 2, message = "税率最多 3 位整数、2 位小数")
    private BigDecimal taxRate;

    @Schema(description = "是否上架销售；传 null 表示不改状态（新增时缺省下架）")
    private Boolean onShelf;

    @Schema(description = "产品图片 URL —— 由 POST /crm/product/image 上传后返回")
    @Size(max = 255, message = "产品图片 URL 长度不能超过 255")
    private String imageUrl;

    @Schema(description = "产品描述")
    private String description;

    @Schema(description = "备注（前端表单暂未开放该项）")
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
