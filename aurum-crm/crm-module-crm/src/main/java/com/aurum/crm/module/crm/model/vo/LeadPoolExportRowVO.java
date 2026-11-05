package com.aurum.crm.module.crm.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 线索池导出行（{@code GET /crm/lead-pool/export}），线索基础列 + 线索池专属列。
 */
@Data
public class LeadPoolExportRowVO {

    @ExcelProperty("线索编号")
    private String leadNo;

    @ExcelProperty("线索名称")
    private String leadName;

    @ExcelProperty("联系人")
    private String contactName;

    @ExcelProperty("职务")
    private String position;

    @ExcelProperty("电话")
    private String phone;

    @ExcelProperty("行业")
    private String industry;

    @ExcelProperty("来源")
    private String source;

    @ExcelProperty("省")
    private String province;

    @ExcelProperty("市")
    private String city;

    @ExcelProperty("区县")
    private String district;

    @ExcelProperty("地址")
    private String address;

    @ExcelProperty("进入线索池时间")
    private String enterPoolTime;

    @ExcelProperty("进入线索池原因")
    private String enterPoolReason;

    @ExcelProperty("进入线索池原因备注")
    private String enterPoolRemark;

    @ExcelProperty("前负责人")
    private String prevOwnerName;

    @ExcelProperty("创建时间")
    private String createTime;
}
