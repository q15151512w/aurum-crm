package com.aurum.crm.module.crm.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 报价单导出行（{@code GET /crm/quotation/export}），字段顺序即 Excel 列顺序。
 */
@Data
public class QuotationExportRowVO {

    @ExcelProperty("报价单编号")
    private String quotationNo;

    @ExcelProperty("名称")
    private String quotationName;

    @ExcelProperty("客户")
    private String customerName;

    @ExcelProperty("版本")
    private String version;

    @ExcelProperty("含税总额")
    private String totalWithTax;

    @ExcelProperty("金额")
    private String totalAmount;

    @ExcelProperty("税额")
    private String taxAmount;

    @ExcelProperty("有效日期")
    private String validDate;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("负责人")
    private String ownerName;

    @ExcelProperty("创建时间")
    private String createTime;
}
