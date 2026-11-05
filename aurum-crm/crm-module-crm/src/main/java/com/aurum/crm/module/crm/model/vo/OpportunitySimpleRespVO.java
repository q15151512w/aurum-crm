package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 商机选择器响应，只有 {@code constants/selectorRegistry.js} 里
 * {@code opportunity} 选择器真正读的三列（外加前端到处都要的 {@code id}）。
 *
 * <p>{@code customerId} / {@code products} 是**合同表单**要的（{@code views/crm/contract/form-config.js}）：
 * 选中商机后要把客户带出来（用户不用再手选一遍，也避免选成别家的客户），
 * 商机**没有报价单**时产品回退成商机自己的明细 —— 于是「商机直签合同」也能把产品带出来。
 *
 * <p>《05-接口文档》第十节早期版本要求返回 {@code id / name / extra}，前端选择器
 * 实际用的是**表格列**，不看 {@code extra} —— 产品模块已经按这个结论把 {@code extra} 去掉了，
 * 这里保持一致。
 */
@Data
@Schema(description = "商机选择器响应")
public class OpportunitySimpleRespVO {

    @Schema(description = "商机ID")
    private Long id;

    @Schema(description = "商机名称")
    private String name;

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "客户名称（跨表补值）")
    private String customerName;

    @Schema(description = "负责人姓名（跨表补值）")
    private String ownerName;

    /**
     * 产品明细。
     *
     * <p>合同表单的兜底数据源：商机**没有报价单**时，合同的产品就照商机自己的明细来
     * （与报价单选择器的 {@code products} 同一结构、同一取舍 —— 见 {@code QuotationSimpleRespVO}）。
     * 没有明细时是**空数组**而不是 {@code null}。
     */
    @Schema(description = "产品明细（合同表单用；商机无报价单时按它带出；无明细时为空数组）")
    private List<DocumentItemRespVO> products;
}
