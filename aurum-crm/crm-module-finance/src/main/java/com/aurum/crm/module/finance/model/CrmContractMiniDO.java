package com.aurum.crm.module.finance.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("crm_contract")
public class CrmContractMiniDO {
    @TableId private Long contractId;
    private String contractNo;
    private String contractName;
    private Long customerId;
    private Long ownerUserId;
    private Long deptId;
    private BigDecimal amount;
    private Integer auditStatus;
    private Integer status;
    @TableLogic private Integer delFlag;
}
