package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 报价单选择器响应：主表只取真正被读的几列，外加合同表单回显要用的产品明细。
 *
 * <p>两处在用，字段取并集：
 * <ul>
 *     <li>{@code constants/selectorRegistry.js} 的 {@code quotation} 选择器 → {@code name / quotationNo / customerName}</li>
 *     <li>合同表单的「报价单编号」选择器（{@code views/crm/contract/form-config.js}）→ {@code quotationNo}
 *       + <b>{@code products}</b>（选中后带出产品明细）</li>
 * </ul>
 * 外加前端到处都要的 {@code id}。《05-接口文档》第十节早期版本要求的 {@code extra} 已不返回
 * （前端选择器用的是表格列，不看 {@code extra}）—— 与产品 / 商机模块保持一致。
 */
@Data
@Schema(description = "报价单选择器响应")
public class QuotationSimpleRespVO {

    @Schema(description = "报价单ID")
    private Long id;

    @Schema(description = "报价单名称")
    private String name;

    @Schema(description = "报价单编号")
    private String quotationNo;

    @Schema(description = "关联商机ID（对应 crm_quotation.opportunity_id，可空）")
    private Long opportunityId;

    /**
     * 关联商机名称。
     *
     * <p>合同表单选中报价单后要**同时把商机对齐**成这张报价单的商机（口径见
     * {@code ContractRelationSupport}）；只传 id 的话，表单上的商机名称会停在旧值或空值，
     * 于是「界面显示 A、落库 B」—— 这正是这类联动最难查的失败方式。
     */
    @Schema(description = "关联商机名称（跨表补值；报价单没挂商机时为 null）")
    private String opportunityName;

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "客户名称（跨表补值）")
    private String customerName;

    /**
     * 产品明细。
     *
     * <p>合同表单的「报价单编号」选择器靠它**回显**：选中一张报价单后，前端要把这些行
     * 直接填进合同明细（{@code ProductItemEditor}），所以必须随选择器一起下发 ——
     * 否则用户选完报价单，产品的位置还是一片空白。
     *
     * <p>结构与详情接口的 {@code products} 相同（{@link DocumentItemRespVO}），
     * 字段名逐字对齐前端明细编辑器（{@code productId / productName / unitName /
     * quantity / unitPrice / discountRate / taxRate / amount / remark}）。
     *
     * <p>没有明细时是**空数组**而不是 {@code null}（前端拿到就能直接铺表；
     * 全局 {@code default-property-inclusion: non_null} 只过滤 null，空数组照常序列化）。
     */
    @Schema(description = "产品明细（合同表单选中报价单后带出；无明细时为空数组）")
    private List<DocumentItemRespVO> products;
}
