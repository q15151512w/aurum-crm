package com.aurum.crm.module.finance.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 回款导出行。表头顺序即字段声明顺序。
 *
 * <p>「合同」「客户」「归属人」三列：finance 模块不依赖 crm，无法跨表补名，
 * 暂导出原始 ID，待共享 mini mapper 落地后再补名。
 */
@Data
public class PaymentExportRowVO {

    @ExcelProperty("回款编号")
    private String paymentNo;

    @ExcelProperty("合同ID")
    private String contractId;

    @ExcelProperty("客户ID")
    private String customerId;

    @ExcelProperty("回款金额")
    private BigDecimal amount;

    @ExcelProperty("回款方式")
    private String paymentMethod;

    @ExcelProperty("回款日期")
    private LocalDate paymentDate;

    @ExcelProperty("收款账户")
    private String accountName;

    @ExcelProperty("审核状态")
    private String auditStatus;

    @ExcelProperty("单据状态")
    private String status;

    @ExcelProperty("归属人ID")
    private String ownerUserId;

    @ExcelProperty("创建时间")
    private LocalDateTime createTime;
}
