package com.aurum.crm.module.system.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class NumberingRuleRespVO {
    private Long id;
    private String name;
    private String code;
    private String prefix;
    private String dateFormat;
    private Integer seqLength;
    private String resetCycle;
    private Boolean enabled;
    private String example;
    private List<NumberingRuleItemRespVO> items;
}
