package com.aurum.crm.module.crm.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 产品类型（分类）新增 / 修改请求，字段名对齐前端 {@code product/category.vue}。
 *
 * <p>前端表单只有三个字段：{@code name}、{@code code}、{@code remark}。
 *
 * <p><b>关于 {@code code}（类型编号）</b>：前端表单里它是必填输入框，但
 * {@code crm_product_category} 表没有编号列，当前采用临时方案 ——
 * <b>类型编号 = 分类ID</b>（读接口里派生返回）。因此这里**刻意不声明 {@code code}**：
 * 前端提交的这个值不会被采纳（Spring Boot 默认忽略未知字段，不会报错），
 * 列表与选择器显示的是分类 id。详见 {@code docs/07-产品模块字段契约.md}。
 */
@Data
@Schema(description = "产品类型新增/修改")
public class ProductCategorySaveReqVO {

    @Schema(description = "类型名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "手机数码")
    @NotBlank(message = "类型名称不能为空")
    @Size(max = 64, message = "类型名称长度不能超过 64")
    private String name;

    @Schema(description = "父分类ID（前端暂时没有层级 UI，缺省 0 顶级）", example = "0")
    private Long parentId;

    @Schema(description = "排序（升序），缺省 0")
    private Integer sort;

    @Schema(description = "状态（0停用 1启用），缺省 1")
    private Integer status;

    @Schema(description = "备注")
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
