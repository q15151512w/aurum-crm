package com.aurum.crm.module.crm.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_contract_item")
public class ContractItemDO extends BaseDO {
    @TableId private Long itemId;
    private Long contractId;
    private Long productId;
    private String productName;
    private Long productTypeId;
    private Long unitId;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountRate;
    private BigDecimal taxRate;
    private BigDecimal amount;
    private Integer sort;
    private String remark;
}
