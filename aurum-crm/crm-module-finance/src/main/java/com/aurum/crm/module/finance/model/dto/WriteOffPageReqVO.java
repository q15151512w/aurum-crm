package com.aurum.crm.module.finance.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 核销记录分页请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WriteOffPageReqVO extends PageParam {

    /** 发票编号（模糊） */
    private String invoiceNo;

    /** 回款编号（模糊） */
    private String paymentNo;

    /** 客户ID（精确） */
    private Long customerId;
}
