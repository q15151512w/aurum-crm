package com.aurum.crm.module.crm.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 产品列表查询请求。
 *
 * <p>前端 {@code ProList} 固定只发这四个参数：{@code pageNo}、{@code pageSize}、
 * {@code keyword}、{@code scope}（见 {@code components/ProList.vue} 与 {@code useTable.js}）。
 * 其余筛选参数保留给 Knife4j / curl 调试用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "产品分页查询")
public class ProductPageReqVO extends PageParam {

    @Schema(description = "关键字：同时匹配产品名称或产品编码")
    private String keyword;

    @Schema(description = "上架筛选（前端筛选胶囊）：all=全部 / onShelf=已上架 / offShelf=未上架",
            example = "all")
    private String scope;

    @Schema(description = "产品类型ID（含该分类及其所有子分类）")
    private Long categoryId;

    @Schema(description = "计量单位ID")
    private Long unitId;

    @Schema(description = "创建时间-开始", example = "2026-01-01 00:00:00")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTimeStart;

    @Schema(description = "创建时间-结束", example = "2026-12-31 23:59:59")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTimeEnd;
}
