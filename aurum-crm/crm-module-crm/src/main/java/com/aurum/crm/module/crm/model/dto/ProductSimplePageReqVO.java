package com.aurum.crm.module.crm.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品选择器（{@code /crm/product/simple-list}）查询请求。
 *
 * <p>前端只发 {@code pageNo}/{@code pageSize}/{@code keyword}。
 *
 * <p><b>选择器固定只返回上架产品</b>：下架产品不应该被商机 / 合同引用。
 * （早期版本有个 {@code includeOffShelf} 开关，但没有任何调用方，已删除。）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "产品选择器分页查询")
public class ProductSimplePageReqVO extends PageParam {

    @Schema(description = "关键字：同时匹配产品名称或产品编码")
    private String keyword;
}
