package com.aurum.crm.module.crm.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 线索导出行（{@code GET /crm/lead/export}），字段顺序即 Excel 列顺序。
 */
@Data
public class LeadExportRowVO {

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

    @ExcelProperty("负责人")
    private String ownerName;

    @ExcelProperty("进入线索池时间")
    private String enterPoolTime;

    @ExcelProperty("进入线索池原因")
    private String enterPoolReason;

    @ExcelProperty("最近跟进时间")
    private String lastFollowTime;

    @ExcelProperty("创建时间")
    private String createTime;
}
