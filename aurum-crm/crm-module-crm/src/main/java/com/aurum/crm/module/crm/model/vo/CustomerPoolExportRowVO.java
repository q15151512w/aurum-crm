package com.aurum.crm.module.crm.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 公海导出行（{@code GET /crm/customer-pool/export}），客户基础列 + 公海专属列。
 */
@Data
public class CustomerPoolExportRowVO {

    @ExcelProperty("客户编号")
    private String customerNo;

    @ExcelProperty("客户名称")
    private String customerName;

    @ExcelProperty("行业")
    private String industry;

    @ExcelProperty("客户级别")
    private String level;

    @ExcelProperty("成交状态")
    private String dealStatus;

    @ExcelProperty("来源")
    private String source;

    @ExcelProperty("联系电话")
    private String phone;

    @ExcelProperty("省")
    private String province;

    @ExcelProperty("市")
    private String city;

    @ExcelProperty("区县")
    private String district;

    @ExcelProperty("详细地址")
    private String address;

    @ExcelProperty("进入公海时间")
    private String enterPoolTime;

    @ExcelProperty("进入原因")
    private String enterPoolReason;

    @ExcelProperty("进入原因备注")
    private String enterPoolRemark;

    @ExcelProperty("前负责人")
    private String prevOwnerName;
}
