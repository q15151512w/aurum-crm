package com.aurum.crm.module.finance.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 可开票回款候选项 —— 原型「回款信息」子表里可选的那一行。
 *
 * <p>已过滤掉「可开余额为 0」的回款：{@code remainingAmount} 必然大于 0。
 */
@Data
@Schema(description = "可开票回款候选")
public class InvoiceablePaymentVO {

    @Schema(description = "回款ID")
    private Long paymentId;

    @Schema(description = "回款编号")
    private String paymentNo;

    @Schema(description = "关联合同ID")
    private Long contractId;

    @Schema(description = "关联合同编号")
    private String contractNo;

    @Schema(description = "关联合同名称")
    private String contractName;

    @Schema(description = "回款方式(字典值)")
    private Integer paymentMethod;

    @Schema(description = "回款方式(展示文本)")
    private String paymentMethodName;

    @Schema(description = "回款日期")
    private LocalDate paymentDate;

    @Schema(description = "回款金额(含税)")
    private BigDecimal amount;

    @Schema(description = "已被其他发票占用的开票金额(含税)")
    private BigDecimal allocatedAmount;

    @Schema(description = "剩余可开票金额(含税) = amount - allocatedAmount")
    private BigDecimal remainingAmount;

    @Schema(description = "本发票已对该回款开票的金额(新增时为 0；编辑时用于回显该行)")
    private BigDecimal writeOffAmount;
}
