package com.aurum.crm.module.system.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class NumberingRuleSaveReqVO {
    @Size(max = 64) private String name;
    @Size(max = 32) private String code;
    @Size(max = 32) private String prefix;
    @Size(max = 32) private String dateFormat;
    @Min(1) @Max(10) private Integer seqLength;
    private String resetCycle;
    private Boolean enabled;
    @Size(max = 255) private String remark;
    private List<NumberingRuleItemSaveReqVO> items;
}
