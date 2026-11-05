package com.aurum.crm.module.finance.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 核销记录 VO —— {@code fin_invoice_payment} 明细的平铺视图。
 *
 * <p>「核销」在数据上就是发票-回款明细（{@code write_off_amount}）。本 VO 把明细
 * 连同发票号、回款号、客户等展示字段平铺出来，供独立核销列表接口一次性查询，
 * 而不必逐张发票点进详情。
 */
@Data
@Schema(description = "核销记录")
public class WriteOffRespVO {

    @Schema(description = "明细ID")
    private Long id;

    @Schema(description = "发票ID")
    private Long invoiceId;

    @Schema(description = "发票编号")
    private String invoiceNo;

    @Schema(description = "回款ID")
    private Long paymentId;

    @Schema(description = "回款编号")
    private String paymentNo;

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "合同ID")
    private Long contractId;

    @Schema(description = "本次核销金额(含税)")
    private BigDecimal writeOffAmount;

    @Schema(description = "回款日期")
    private LocalDate paymentDate;

    @Schema(description = "核销时间")
    private LocalDateTime createTime;
}
