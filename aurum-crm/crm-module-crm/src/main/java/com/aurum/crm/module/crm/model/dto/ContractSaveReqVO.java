package com.aurum.crm.module.crm.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ContractSaveReqVO {
    @NotBlank private String contractName;
    @NotNull private Long customerId;
    private Long opportunityId;
    private Long quotationId;
    private Integer contractType = 1;
    private BigDecimal amount;
    private Long ownerUserId;
    private LocalDate signDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private String customerSigner;
    private String companySigner;
    private String remark;
    @Valid @NotEmpty private List<ContractItemReqVO> items;
}
