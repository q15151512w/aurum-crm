package com.aurum.crm.module.finance.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("crm_customer")
public class CrmCustomerMiniDO {
    @TableId private Long customerId;
    private String customerName;
    @TableLogic private Integer delFlag;
}
