package com.aurum.crm.module.finance.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 发票明细子项：这笔回款本次开多少钱。
 *
 * <p>金额是<b>含税</b>口径（与 {@code fin_payment.amount} 一致），因为业务上
 * 「本次开票金额」就是拿回款金额在发票之间做分摊，不该出现中间换算。
 * 不含税金额与税额由服务端按发票级税率逐行反算，见
 * {@code InvoiceServiceImpl#computeAmounts}。
 */
@Data
public class InvoicePaymentReqVO {

    @NotNull(message = "回款ID不能为空")
    private Long paymentId;

    /** 本次开票金额（含税）。必须为正：0 元明细只是噪音，负数是冲销，本功能都不支持 */
    @NotNull(message = "本次开票金额不能为空")
    @DecimalMin(value = "0.01", message = "本次开票金额必须大于 0")
    private BigDecimal writeOffAmount;
}
