package com.aurum.crm.module.crm.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 商机导出行（{@code GET /crm/opportunity/export}），字段顺序即 Excel 列顺序。
 * 所有字段都预转成 String（码值转中文、时间格式化）。
 */
@Data
public class OpportunityExportRowVO {

    @ExcelProperty("商机编号")
    private String opportunityNo;

    @ExcelProperty("商机名称")
    private String opportunityName;

    @ExcelProperty("客户")
    private String customerName;

    @ExcelProperty("金额")
    private String amount;

    @ExcelProperty("阶段")
    private String stage;

    @ExcelProperty("销售人")
    private String ownerName;

    @ExcelProperty("来源")
    private String source;

    @ExcelProperty("预计成交日")
    private String expectedCloseDate;

    @ExcelProperty("创建时间")
    private String createTime;
}
