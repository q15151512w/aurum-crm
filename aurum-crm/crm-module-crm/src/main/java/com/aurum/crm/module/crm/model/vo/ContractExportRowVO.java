package com.aurum.crm.module.crm.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 合同导出行（{@code GET /crm/contract/export}），字段顺序即 Excel 列顺序。
 */
@Data
public class ContractExportRowVO {

    @ExcelProperty("合同编号")
    private String contractNo;

    @ExcelProperty("合同名称")
    private String contractName;

    @ExcelProperty("客户")
    private String customerName;

    @ExcelProperty("合同类型")
    private String contractType;

    @ExcelProperty("金额")
    private String amount;

    @ExcelProperty("销售人")
    private String ownerName;

    @ExcelProperty("签订日期")
    private String signDate;

    @ExcelProperty("开始日期")
    private String startDate;

    @ExcelProperty("结束日期")
    private String endDate;

    @ExcelProperty("审核状态")
    private String auditStatus;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("创建时间")
    private String createTime;
}
