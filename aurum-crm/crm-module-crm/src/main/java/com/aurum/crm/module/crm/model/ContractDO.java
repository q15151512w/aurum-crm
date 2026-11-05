package com.aurum.crm.module.crm.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_contract")
public class ContractDO extends BaseDO {
    @TableId private Long contractId;
    private String contractNo;
    private String contractName;
    private Long customerId;
    private Long opportunityId;
    private Long quotationId;
    private Integer contractType;
    private BigDecimal amount;
    private Long ownerUserId;
    private Long deptId;
    private LocalDate signDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private String customerSigner;
    private String companySigner;
    private Integer auditStatus;
    private Integer status;
    private String remark;
}
