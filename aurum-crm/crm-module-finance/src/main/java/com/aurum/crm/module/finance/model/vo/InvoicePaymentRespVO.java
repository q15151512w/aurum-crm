package com.aurum.crm.module.finance.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 发票明细行（含回款方的展示字段）。
 *
 * <p>回款编号 / 方式 / 日期 / 回款金额都是<b>补值</b>出来的：明细表只存
 * {@code paymentId} 与金额，其余字段从 {@code fin_payment} 现查，避免冗余列不同步。
 */
@Data
@Schema(description = "发票回款明细")
public class InvoicePaymentRespVO {

    @Schema(description = "明细ID")
    private Long id;

    @Schema(description = "回款ID")
    private Long paymentId;

    @Schema(description = "回款编号")
    private String paymentNo;

    @Schema(description = "回款方式(字典值)")
    private Integer paymentMethod;

    @Schema(description = "回款方式(展示文本)")
    private String paymentMethodName;

    @Schema(description = "回款日期")
    private LocalDate paymentDate;

    @Schema(description = "该笔回款总金额(含税)")
    private BigDecimal paymentAmount;

    @Schema(description = "本次开票金额(含税)")
    private BigDecimal writeOffAmount;
}
