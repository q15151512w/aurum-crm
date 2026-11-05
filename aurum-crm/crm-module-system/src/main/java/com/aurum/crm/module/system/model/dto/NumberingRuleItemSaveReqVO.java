package com.aurum.crm.module.system.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NumberingRuleItemSaveReqVO {
    private Long id;
    @Size(max = 32) private String type;
    @Size(max = 255) private String value;
    @Min(1) @Max(32) private Integer length;
    @Min(0) private Integer sort;
    private Boolean sequenceBasis;
}
