package com.aurum.crm.module.crm.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 客户导出行（{@code GET /crm/customer/export}），字段顺序即 Excel 列顺序。
 * 所有字段都预转成 String（码值转中文、时间格式化）—— 交给 Service 填，避免 EasyExcel 对类型做二次推断。
 */
@Data
public class CustomerExportRowVO {

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

    @ExcelProperty("负责人")
    private String ownerName;

    @ExcelProperty("创建时间")
    private String createTime;
}
