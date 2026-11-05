package com.aurum.crm.module.crm.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户选择器（{@code GET /crm/customer/simple-list}）的分页查询请求。
 *
 * <p>{@code constants/selectorRegistry.js} 里 {@code customer} 选择器的搜索占位是
 * 「客户名称 / 负责人」，关键字先匹配客户名称即可。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "客户选择器查询")
public class CustomerSimplePageReqVO extends PageParam {

    @Schema(description = "关键字：匹配客户名称")
    private String keyword;
}
