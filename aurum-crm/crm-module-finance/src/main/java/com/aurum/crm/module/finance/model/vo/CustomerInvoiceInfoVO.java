package com.aurum.crm.module.finance.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 客户开票信息（默认行）。
 *
 * <p>{@code null} 表示该客户还没维护开票信息 —— 这是正常分支，前端应把六个
 * 输入框留空让用户手填，而不是报错。字段名与 {@code fin_invoice} 的快照列
 * 逐字相同，前端可直接整体铺进表单。
 */
@Data
@Schema(description = "客户开票信息(默认行)")
public class CustomerInvoiceInfoVO {

    private String invoiceTitle;
    private String taxNo;
    private String bankName;
    private String bankAccount;
    private String invoiceAddr;
    private String invoicePhone;
}
