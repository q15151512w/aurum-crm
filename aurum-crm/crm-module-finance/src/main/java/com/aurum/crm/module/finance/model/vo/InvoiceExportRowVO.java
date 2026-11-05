package com.aurum.crm.module.finance.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发票导出行。表头顺序即字段声明顺序。
 *
 * <p>「合同」「客户」列：finance 模块不依赖 crm，无法跨表补名，
 * 暂导出原始 ID，待共享 mini mapper 落地后再补名。
 */
@Data
public class InvoiceExportRowVO {

    @ExcelProperty("申请编号")
    private String applyNo;

    @ExcelProperty("发票号")
    private String invoiceNo;

    @ExcelProperty("合同ID")
    private String contractId;

    @ExcelProperty("客户ID")
    private String customerId;

    @ExcelProperty("发票类型")
    private String invoiceType;

    @ExcelProperty("不含税金额")
    private BigDecimal amount;

    @ExcelProperty("税额")
    private BigDecimal taxAmount;

    @ExcelProperty("价税合计")
    private BigDecimal totalAmount;

    @ExcelProperty("申请日期")
    private LocalDate applyDate;

    @ExcelProperty("审核状态")
    private String auditStatus;

    @ExcelProperty("单据状态")
    private String status;

    @ExcelProperty("创建时间")
    private LocalDateTime createTime;
}
