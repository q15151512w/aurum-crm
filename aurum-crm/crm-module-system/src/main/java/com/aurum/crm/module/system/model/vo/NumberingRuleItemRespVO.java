package com.aurum.crm.module.system.model.vo;

import lombok.Data;

@Data
public class NumberingRuleItemRespVO {
    private Long id;
    private String type;
    private String value;
    private Integer length;
    private Integer sort;
    private Boolean sequenceBasis;
}
