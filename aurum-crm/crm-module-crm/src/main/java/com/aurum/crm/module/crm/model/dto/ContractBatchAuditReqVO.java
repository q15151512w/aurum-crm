package com.aurum.crm.module.crm.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ContractBatchAuditReqVO {
    @NotEmpty
    private List<Long> contractIds;
    @NotNull
    private Integer result;
    private String comment;
}
