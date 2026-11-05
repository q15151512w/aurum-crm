package com.aurum.crm.module.crm.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报价单选择器（{@code GET /crm/quotation/simple-list}）的分页查询请求。
 *
 * <p>两处在用：
 * <ul>
 *     <li>{@code constants/selectorRegistry.js} 的 {@code quotation} 选择器
 *       （列：报价单名称 / 报价单编号 / 客户名称，搜索框占位「报价单名称 / 商机名称」）</li>
 *     <li>合同表单的「报价单编号」选择器（{@code views/crm/contract/form-config.js}）</li>
 * </ul>
 *
 * <p><b>{@code opportunityId} / {@code customerId} 是合同表单加的收窄条件</b>
 * （经 {@code EntitySelectorDialog} 的 {@code requestParams} 传入，客户签约人也是这么传的）：
 * 选了商机就只列**这个商机**的报价单，没选商机就只列**这个客户**的报价单 ——
 * 否则用户很容易选中别家客户/别的商机的报价单，而合同侧的一致性校验会把它拒掉（{@code 2304}）。
 *
 * <p>两个都可空：都不传时行为与改动前完全一致（列数据权限内全部报价单）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "报价单选择器查询")
public class QuotationSimplePageReqVO extends PageParam {

    @Schema(description = "关键字：匹配报价单名称")
    private String keyword;

    @Schema(description = "只列该商机下的报价单（可空；合同表单按所选商机传）")
    private Long opportunityId;

    @Schema(description = "只列该客户的报价单（可空；合同表单在没选商机时按所选客户传）")
    private Long customerId;
}
