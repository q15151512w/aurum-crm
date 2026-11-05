package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 销售漏斗的一行，对应 {@code GET /crm/opportunity/funnel}。
 *
 * <p>形状与前端 {@code views/dashboard/components/FunnelChart.vue} 的
 * {@code data} prop 完全一致（{@code [{ stage, count, amount }]}，{@code stage} 是中文标签），
 * 也与 {@code api/opportunity.js} 的 {@code mockFunnel} 一致。
 *
 * <p><b>为什么固定返回 5 行</b>：漏斗图画的是「越往下越窄」的推进过程，
 * 输单（终态、概率 0）不属于推进过程，前端配色 {@code STAGE_COLORS} 也只给了 5 种颜色。
 * 所以这里固定返回 初步接洽 → 需求确认 → 方案报价 → 商务谈判 → 赢单 五行，
 * 没有数据的阶段也返回（{@code count = 0}），避免前端图表因缺行而错位。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "销售漏斗统计行")
public class OpportunityFunnelRespVO {

    @Schema(description = "阶段（中文标签）", example = "商务谈判")
    private String stage;

    @Schema(description = "该阶段的商机数量", example = "8")
    private Long count;

    @Schema(description = "该阶段的预计金额合计", example = "180000.00")
    private BigDecimal amount;
}
