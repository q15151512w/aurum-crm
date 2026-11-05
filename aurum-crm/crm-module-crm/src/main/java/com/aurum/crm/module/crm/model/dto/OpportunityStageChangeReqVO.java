package com.aurum.crm.module.crm.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 变更阶段请求，对应 {@code PUT /crm/opportunity/{id}/stage}。
 *
 * <p>请求体就是前端详情页「变更阶段」弹窗的表单对象
 * （{@code views/crm/opportunity/detail.vue} 的 {@code stageFields}），只有两个键：
 * <ul>
 *     <li>{@code stage} —— 目标阶段，**中文标签**。当前阶段在弹窗里已被剔除，
 *       选它等于什么都没干；即便如此后端仍做「同阶段不写日志」的幂等处理，
 *       因为接口是可以被直接调用的</li>
 *     <li>{@code remark} —— 变更说明（选填），落 {@code crm_opportunity_stage_log.change_reason}，
 *       对外字段名叫 {@code remark}</li>
 * </ul>
 */
@Data
@Schema(description = "商机变更阶段")
public class OpportunityStageChangeReqVO {

    @Schema(description = "目标阶段（中文标签）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "商务谈判")
    @NotBlank(message = "请选择要变更到的阶段")
    @Size(max = 16, message = "阶段取值过长")
    private String stage;

    @Schema(description = "变更说明（选填，记入阶段变更历史）")
    @Size(max = 500, message = "变更说明长度不能超过 500")
    private String remark;
}
