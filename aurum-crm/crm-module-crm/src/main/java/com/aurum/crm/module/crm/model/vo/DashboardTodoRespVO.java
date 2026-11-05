package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 首页看板「待办事项」区块，对应 {@code GET /dashboard/todo}（《05-接口文档》9.1）。
 *
 * <p>字段名与前端 {@code views/dashboard/constants.js} 的 {@code TODO_META}
 * 的 {@code key} 一一对应 —— 视图就是靠这个 key 把数值映射成卡片的，对不上会显示成 {@code --}。
 *
 * <p>六个计数**全部受数据权限约束**：非管理员只统计自己权限范围内的数据。
 * 这不是额外写的代码，而是 {@code DataPermissionInnerInterceptor} 对
 * {@code crm_lead} / {@code crm_customer} / {@code crm_opportunity} / {@code crm_contract} /
 * {@code fin_payment} / {@code fin_invoice} 的自动追加。
 */
@Data
@Schema(description = "首页看板-待办事项")
public class DashboardTodoRespVO {

    @Schema(description = "待回访客户：下次联系时间已过 且 未成交", example = "5")
    private Long pendingVisitCustomer;

    @Schema(description = "待处理线索：未转化的线索", example = "8")
    private Long pendingLead;

    @Schema(description = "待处理商机：未赢单也未输单", example = "20")
    private Long pendingOpportunity;

    @Schema(description = "待审核合同：审核状态为「审批中」", example = "12")
    private Long pendingContractAudit;

    @Schema(description = "待财务审核：回款单审核状态为「审批中」", example = "20")
    private Long pendingFinanceAudit;

    @Schema(description = "待财务开票：发票状态为「待开票」", example = "56")
    private Long pendingInvoice;
}
