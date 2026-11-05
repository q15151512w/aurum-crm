package com.aurum.crm.module.crm.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_contact")
public class ContactDO extends BaseDO {
    @TableId private Long contactId;
    private Long customerId;
    private String contactName;
    private String position;
    private String phone;
    private String telephone;
    private String email;
    private String wechat;
    private Integer gender;
    private Integer isPrimary;
    private String remark;
}
