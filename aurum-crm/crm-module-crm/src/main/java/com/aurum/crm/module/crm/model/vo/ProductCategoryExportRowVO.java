package com.aurum.crm.module.crm.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 产品分类导出行（{@code GET /crm/product-category/export}），字段顺序即 Excel 列顺序。
 */
@Data
public class ProductCategoryExportRowVO {

    @ExcelProperty("分类名称")
    private String categoryName;

    @ExcelProperty("父级")
    private String parentName;

    @ExcelProperty("排序")
    private String sort;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("创建时间")
    private String createTime;
}
