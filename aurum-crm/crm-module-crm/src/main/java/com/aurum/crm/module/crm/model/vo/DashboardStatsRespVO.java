package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 首页看板「实时数据」区块，对应 {@code GET /dashboard/stats}（《05-接口文档》9.2）。
 *
 * <p>字段名与前端 {@code REALTIME_META} 的 {@code key} 一一对应。
 *
 * <h3>⚠️ 金额三项的字段级权限</h3>
 *
 * <p>{@code salesAmount} / {@code receivedAmount} / {@code invoicedAmount}
 * 对**非财务角色返回 {@code null}**（《05-接口文档》1.7 字段权限），
 * 前端已做 {@code --} 兜底。判定见 {@code DashboardServiceImpl#canViewAmount()}：
 * 角色标识含 {@code admin} 或 {@code finance} 才放行。
 *
 * <p>数量四项不做权限限制 —— 它们是经营概况，不是敏感金额。
 */
@Data
@Schema(description = "首页看板-实时数据")
public class DashboardStatsRespVO {

    @Schema(description = "新增线索(个)", example = "15")
    private Long leadCount;

    @Schema(description = "新增客户(个)", example = "6")
    private Long customerCount;

    @Schema(description = "新增商机(个)", example = "8")
    private Long opportunityCount;

    @Schema(description = "新增合同(个)", example = "3")
    private Long contractCount;

    @Schema(description = "销售总额(元)：区间内合同金额合计。非财务角色为 null", example = "82000.00")
    private BigDecimal salesAmount;

    @Schema(description = "已收款金额(元)：区间内已审核通过的回款合计。非财务角色为 null", example = "40500.00")
    private BigDecimal receivedAmount;

    @Schema(description = "已开票金额(元)：区间内已开票的价税合计。非财务角色为 null", example = "15500.00")
    private BigDecimal invoicedAmount;
}
