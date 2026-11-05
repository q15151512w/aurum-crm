package com.aurum.crm.module.crm.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商机阶段变更历史响应，对应 {@code crm_opportunity_stage_log}。
 *
 * <p>字段名对齐前端 {@code views/crm/opportunity/detail.vue}「阶段变更历史」Tab 的表格列
 * （{@code fromStage} / {@code toStage} / {@code operator} / {@code time} / {@code remark}），
 * 前端一行都不用改。
 *
 * <p>接口返回的是**数组不是分页对象**：前端 {@code getOpportunityStageLog} 拿到就是
 * {@code log || []} 直接塞进表格，没有分页组件。变更次数天然有限，不需要分页。
 */
@Data
@Schema(description = "商机阶段变更记录")
public class OpportunityStageLogRespVO {

    @Schema(description = "记录ID（对应 log_id）")
    private Long id;

    @Schema(description = "原阶段（中文标签；首次落库时为 null）", example = "需求确认")
    private String fromStage;

    @Schema(description = "新阶段（中文标签）", example = "商务谈判")
    private String toStage;

    @Schema(description = "操作人姓名（operator_id → sys_user.real_name）")
    private String operator;

    @Schema(description = "操作时间（对应 create_time）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime time;

    @Schema(description = "变更说明（对应 change_reason）")
    private String remark;
}
