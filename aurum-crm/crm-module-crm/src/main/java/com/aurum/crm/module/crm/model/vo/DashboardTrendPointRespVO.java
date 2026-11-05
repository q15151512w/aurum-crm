package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 首页看板「业绩趋势」的一个数据点，对应 {@code GET /dashboard/trend}（《05-接口文档》9.8）。
 *
 * <p>字段名与前端 {@code components/TrendChart.vue} 的 {@code data} prop 一致。
 *
 * <p><b>这个形状此前只存在于前端注释里</b> —— 文档第九节把 {@code funnel} / {@code trend}
 * 标成「预留、未定义响应结构」，所以前端先按 {@code [{date, leadCount, ...}]} 实现了。
 * 本次实现后端时把这个形状**正式定下来并写回文档**，两端从此有共同依据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "首页看板-趋势数据点")
public class DashboardTrendPointRespVO {

    /**
     * X 轴标签。
     *
     * <p>粒度随 {@code range} 变：{@code day} → {@code HH:mm}（小时点）、
     * {@code week} / {@code month} → {@code MM-dd}（日期点）。
     * 前端直接当标签用，不做解析。
     */
    @Schema(description = "X 轴标签（day 为 HH:mm，week/month 为 MM-dd）", example = "09-13")
    private String date;

    @Schema(description = "该点新增线索数", example = "3")
    private Long leadCount;

    @Schema(description = "该点新增客户数", example = "2")
    private Long customerCount;

    @Schema(description = "该点新增合同数", example = "1")
    private Long contractCount;

    /**
     * 该点的合同金额合计。
     *
     * <p>⚠️ 与「实时数据」的金额一样**受字段级权限约束**：非财务角色返回 {@code null}，
     * 前端折线图那条「销售金额」系列会是空的（数量三条照常画）。
     */
    @Schema(description = "该点合同金额合计；非财务角色为 null", example = "20000.00")
    private BigDecimal salesAmount;
}
