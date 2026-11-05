package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 产品类型（分类）响应，字段名对齐前端 {@code product/category.vue} 与
 * 产品表单里的「产品类型」选择器。
 *
 * <p><b>没有 {@code children}</b>：前端把分类当**扁平列表**用
 * （{@code ProList} 要求响应是 {@code {list,total}}，页面也没有层级 UI）。
 * 数据库里的层级（{@code parent_id}/{@code ancestors}）仍然保留，
 * 产品列表的「按分类含子分类筛选」依旧基于它工作。
 *
 * <p><b>{@code code} 是派生值</b>：{@code crm_product_category} 没有编号列，
 * 当前临时用**分类ID**代替（见 {@code docs/07-产品模块字段契约.md}）。
 */
@Data
@Schema(description = "产品类型响应")
public class ProductCategoryRespVO {

    @Schema(description = "类型ID")
    private Long id;

    @Schema(description = "类型名称")
    private String name;

    @Schema(description = "类型编号（临时方案：等于分类ID）", example = "4")
    private String code;

    @Schema(description = "父分类ID（0 表示顶级）")
    private Long parentId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建人姓名（跨表补值）")
    private String creator;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
