package com.aurum.crm.module.crm.model.vo;

import lombok.Data;

@Data
public class ContactRespVO {
    private Long id;
    private Long customerId;
    private String name;
    private String title;
    private String phone;
    private String telephone;
    private String email;
    private String wechat;
    private String gender;
    private Boolean primary;
    private String remark;
}
