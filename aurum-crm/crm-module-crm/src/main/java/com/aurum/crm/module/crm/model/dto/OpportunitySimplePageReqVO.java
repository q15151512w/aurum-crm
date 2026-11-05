package com.aurum.crm.module.crm.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商机选择器（{@code GET /crm/opportunity/simple-list}）的分页查询请求。
 *
 * <p>{@code constants/selectorRegistry.js} 里 {@code opportunity} 选择器的列是
 * 商机名称 / 客户 / 负责人，搜索框占位是「商机名称」，所以这里只需要一个关键字。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "商机选择器查询")
public class OpportunitySimplePageReqVO extends PageParam {

    @Schema(description = "关键字：匹配商机名称")
    private String keyword;
}
