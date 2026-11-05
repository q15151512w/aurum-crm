package com.aurum.crm.module.crm.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ContractItemReqVO {
    private Long productId;
    private String productName;
    private Long productTypeId;
    private Long unitId;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountRate;
    private BigDecimal taxRate;
    private Integer sort;
    private String remark;
}
