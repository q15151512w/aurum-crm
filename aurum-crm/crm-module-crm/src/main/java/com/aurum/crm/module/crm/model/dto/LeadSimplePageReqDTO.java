package com.aurum.crm.module.crm.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 线索选择器（{@code GET /crm/lead/simple-list}）的分页查询请求。
 *
 * <p>{@code constants/selectorRegistry.js} 里 {@code lead} 选择器的搜索占位是
 * 「线索名称」，所以这里只需要一个关键字。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "线索选择器查询")
public class LeadSimplePageReqDTO extends PageParam {

    @Schema(description = "关键字：匹配线索名称")
    private String keyword;
}
