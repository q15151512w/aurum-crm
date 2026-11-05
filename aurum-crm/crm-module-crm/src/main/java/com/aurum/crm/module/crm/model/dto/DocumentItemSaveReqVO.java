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
 * 单据明细行请求体 —— **报价单 / 商机（以及将来的合同）共用同一个结构**
 * （《02-建表文档》3.5：「三张表共用一个明细行结构」），所以类名不再带模块前缀。
 *
 * <p><b>字段名与前端 {@code components/form/ProductItemEditor.vue} 逐字对应</b>：
 * 编辑器提交的每一行就是 {@code productId / productName / unitName / quantity / unitPrice /
 * discountRate / taxRate / amount / remark}。多一个字少一个字都会让明细回填不上或写坏。
 *
 * <p><b>三个「客户端传了也不算数」的字段</b>：
 * <ul>
 *     <li>{@code amount} —— 服务端按《02-建表文档》3.6 重算并覆盖（见 {@code ItemSupport#buildItems}），
 *       声明它只是为了在值对不上时记一条 debug 日志，帮助发现前后端口径漂移</li>
 *     <li>{@code unitId} —— 可传（优先于 {@code unitName}），但只认真实存在于 {@code sys_unit} 的 id</li>
 *     <li>{@code productTypeId} / {@code sort} —— **刻意不声明**：分类由产品的 {@code category_id} 快照、
 *       排序按提交顺序写。客户端传了会被 Jackson 静默丢弃（Boot 默认关闭
 *       {@code FAIL_ON_UNKNOWN_PROPERTIES}），这样就不用维护两套排序规则</li>
 * </ul>
 */
@Data
@Schema(description = "单据明细行（报价单 / 商机共用）")
public class DocumentItemSaveReqVO {

    @Schema(description = "产品ID（选择器写入）；为空表示手填了一个不在产品目录里的名称")
    private Long productId;

    @Schema(description = "产品名称（快照，必填）", requiredMode = Schema.RequiredMode.REQUIRED, example = "CRM 标准版")
    @NotBlank(message = "产品名称不能为空")
    @Size(max = 128, message = "产品名称长度不能超过 128")
    private String productName;

    /** 前端「单位」下拉给的是**名称**（台/个/箱…），服务端按名字解析成 {@code unit_id} */
    @Schema(description = "计量单位名称（前端下拉给的是名字，服务端解析成 unitId）", example = "套")
    @Size(max = 32, message = "计量单位名称长度不能超过 32")
    private String unitName;

    @Schema(description = "计量单位ID（传了则优先用它，否则按 unitName 解析）")
    private Long unitId;

    @Schema(description = "数量，支持 4 位小数", requiredMode = Schema.RequiredMode.REQUIRED, example = "1.0000")
    @DecimalMin(value = "0", message = "数量不能为负数")
    @Digits(integer = 11, fraction = 4, message = "数量最多 11 位整数、4 位小数")
    private BigDecimal quantity;

    @Schema(description = "单价（含税）", requiredMode = Schema.RequiredMode.REQUIRED, example = "29800.00")
    @DecimalMin(value = "0", message = "单价不能为负数")
    @Digits(integer = 13, fraction = 2, message = "单价最多 13 位整数、2 位小数")
    private BigDecimal unitPrice;

    @Schema(description = "折扣率(%)，缺省 0", example = "0.00")
    @DecimalMin(value = "0", message = "折扣率不能为负数")
    @DecimalMax(value = "100", message = "折扣率不能超过 100")
    @Digits(integer = 3, fraction = 2, message = "折扣率最多 3 位整数、2 位小数")
    private BigDecimal discountRate;

    @Schema(description = "税率(%)，缺省 0", example = "6.00")
    @DecimalMin(value = "0", message = "税率不能为负数")
    @DecimalMax(value = "100", message = "税率不能超过 100")
    @Digits(integer = 3, fraction = 2, message = "税率最多 3 位整数、2 位小数")
    private BigDecimal taxRate;

    @Schema(description = "行金额（含税）。⚠️ 仅供服务端比对，落库值一律由服务端按 3.6 重算")
    @DecimalMin(value = "0", message = "行金额不能为负数")
    @Digits(integer = 13, fraction = 2, message = "行金额最多 13 位整数、2 位小数")
    private BigDecimal amount;

    @Schema(description = "备注")
    @Size(max = 255, message = "明细备注长度不能超过 255")
    private String remark;
}
