package com.aurum.crm.module.finance.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 财务拒绝开票的请求体。
 *
 * <p>与 {@link PaymentAuditReqVO} 的驳回分支同一口径：<b>拒绝必须说明原因</b>，
 * 否则申请人拿着一张被退回的单子无从下手。
 *
 * <p>⚠️ 与回款审核现状一致，{@code comment} <b>只校验、不落库</b> ——
 * {@code fin_invoice} 没有存放审核意见的列。要真正留存需先加列
 * （见 {@code InvoiceServiceImpl#reject} 的注释）。
 */
@Data
public class InvoiceRejectReqVO {

    @NotBlank(message = "拒绝时必须填写拒绝原因")
    @Size(max = 500)
    private String comment;
}
