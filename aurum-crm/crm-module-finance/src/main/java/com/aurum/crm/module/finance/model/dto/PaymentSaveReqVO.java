package com.aurum.crm.module.finance.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PaymentSaveReqVO {
    @NotNull(message = "请选择关联合同")
    private Long contractId;

    @NotNull(message = "请输入回款金额")
    @DecimalMin(value = "0.01", message = "回款金额必须大于0")
    private BigDecimal amount;

    @NotNull(message = "请选择回款日期")
    private LocalDate paymentDate;

    @NotNull(message = "请选择回款方式")
    @Min(1) @Max(5)
    private Integer paymentMethod;

    @Size(max = 128) private String accountName;
    @Size(max = 64) private String accountNo;
    @Size(max = 128) private String serialNo;
    @Size(max = 500) private String remark;
}
