package com.aurum.crm.module.crm.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ContractAuditReqVO {
    @NotNull private Integer result;
    private String comment;
}
