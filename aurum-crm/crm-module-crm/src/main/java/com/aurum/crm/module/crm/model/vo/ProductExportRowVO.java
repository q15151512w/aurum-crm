package com.aurum.crm.module.crm.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 产品导出行（{@code GET /crm/product/export}），字段顺序即 Excel 列顺序。
 */
@Data
public class ProductExportRowVO {

    @ExcelProperty("产品编号")
    private String productNo;

    @ExcelProperty("产品名称")
    private String productName;

    @ExcelProperty("分类")
    private String categoryName;

    @ExcelProperty("单位")
    private String unitName;

    @ExcelProperty("售价")
    private String price;

    @ExcelProperty("成本价")
    private String costPrice;

    @ExcelProperty("税率")
    private String taxRate;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("创建时间")
    private String createTime;
}
