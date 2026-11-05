package com.aurum.crm.module.crm.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 报价单新增 / 修改请求，字段名与前端表单模型**逐字一致**。
 *
 * <p>前端提交体就是 {@code views/crm/quotation/index.vue} 里 {@code emptyForm()} 声明过的键
 * （{@code useFormDialog} 提交前会摘掉 {@code id}），所以这里的字段名必须与它一模一样，
 * 前端一行都不用改。
 *
 * <p><b>明细是内嵌的</b>：前端没有独立的明细接口（《05-接口文档》5.2 也没有
 * {@code /crm/quotation/{id}/products}），「产品明细」就是表单里的一个 {@code type: 'items'} 字段，
 * 提交时和主单一起走 {@code products} 数组。所以：
 * <ul>
 *     <li>{@code POST}：{@code products} 缺省视为空数组（允许先建一张没有明细的报价单）</li>
 *     <li>{@code PUT}：{@code products == null} → **不动明细、也不重算金额**；
 *       {@code products} 为数组（含 {@code []}）→ **整份覆盖**（先逻辑删除旧行再插入新行）</li>
 * </ul>
 *
 * <p><b>三个金额字段只用于比对</b>：前端把它们置灰、由明细实时算出（{@code calcItemsTotal}）
 * 后一起提交，但后端一律按《02-建表文档》3.6 从明细**重算并覆盖**（见 {@code MoneyCalculator}）。
 * 声明它们是为了在值对不上时记一条 debug 日志。**不要**按它们落库。
 *
 * <p><b>刻意不声明的字段（会被静默忽略）</b>：{@code customerName} / {@code opportunityName} /
 * {@code ownerName}（选择器写入的显示文本）、{@code discount}（整单折扣**统一取关联商机的折扣**，
 * 报价单不存也不接受这个字段，要改折扣去改商机）、{@code profit} / {@code productCount}
 * （读时派生）。Spring Boot 默认关闭了 {@code FAIL_ON_UNKNOWN_PROPERTIES}，多余字段不会报错
 * —— 与产品模块的 {@code categoryName}、商机模块的同名字段是同一处理方式。
 */
@Data
@Schema(description = "报价单新增/修改")
public class QuotationSaveReqVO {

    @Schema(description = "报价单名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "星辰科技标准版报价")
    @NotBlank(message = "报价单名称不能为空")
    @Size(max = 128, message = "报价单名称长度不能超过 128")
    private String name;

    @Schema(description = "关联客户ID（客户选择器写入）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择客户")
    private Long customerId;

    @Schema(description = "关联商机ID（商机选择器写入，可空）")
    private Long opportunityId;

    @Schema(description = "负责人ID；不传时缺省为当前登录人")
    private Long ownerUserId;

    @Schema(description = "状态（中文标签），不传时缺省「报价中」", example = "报价中")
    @Size(max = 32, message = "状态取值过长")
    private String status;

    /**
     * 报价日期。
     *
     * <p>⚠️ {@code crm_quotation} **没有**这一列，服务端**完全忽略**本字段。
     * 响应里的 {@code quoteDate} 是从 {@code create_time} 派生的（「报价日期就是建单日期」）。
     * 之所以还声明它，是为了让 Swagger 里能看到这个字段存在但无效，而不是让人以为它生效了。
     */
    @Schema(description = "报价日期。⚠️ 无对应列，服务端忽略本字段；响应里的 quoteDate 派生自 createTime",
            example = "2026-09-14")
    @Size(max = 32, message = "报价日期格式不正确")
    private String quoteDate;

    @Schema(description = "报价单有效期至（yyyy-MM-dd；空串表示清空）。前端表单暂无此项")
    @Size(max = 32, message = "有效期格式不正确")
    private String validDate;

    @Schema(description = "产品明细。PUT 时传 null 表示不动明细，传 [] 表示清空")
    @Valid
    @Size(max = 200, message = "单张报价单的产品明细不能超过 200 行")
    private List<DocumentItemSaveReqVO> products;

    @Schema(description = "合计价格（不含税）。⚠️ 仅供服务端比对，落库值由服务端重算")
    private BigDecimal totalAmount;

    @Schema(description = "税额。⚠️ 仅供服务端比对，落库值由服务端重算")
    private BigDecimal taxAmount;

    @Schema(description = "整单最终价（含税）。⚠️ 仅供服务端比对，落库值由服务端重算")
    private BigDecimal finalAmount;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
