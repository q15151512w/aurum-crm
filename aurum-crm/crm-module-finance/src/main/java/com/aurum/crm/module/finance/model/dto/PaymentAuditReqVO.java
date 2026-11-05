package com.aurum.crm.module.finance.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PaymentAuditReqVO {
    @NotNull @Min(1) @Max(2)
    private Integer result;
    @Size(max = 500)
    private String comment;
}
