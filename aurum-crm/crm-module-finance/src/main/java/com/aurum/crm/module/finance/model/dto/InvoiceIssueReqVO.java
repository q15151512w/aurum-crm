package com.aurum.crm.module.finance.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class InvoiceIssueReqVO {
    @NotBlank private String invoiceNo;
    @NotNull private LocalDate issueDate;
    @NotEmpty private List<Long> attachmentIds;
}
