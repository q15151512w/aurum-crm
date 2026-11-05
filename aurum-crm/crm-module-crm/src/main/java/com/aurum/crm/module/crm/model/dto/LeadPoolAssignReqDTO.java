package com.aurum.crm.module.crm.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 线索池分配请求，对应《接口文档》4.2 分配弹窗：负责人 + 备注。
 */
@Data
@Schema(description = "线索池分配")
public class LeadPoolAssignReqDTO {

    @Schema(description = "分配给的负责人ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "负责人不能为空")
    private Long ownerUserId;

    @Schema(description = "分配原因备注")
    private String reasonRemark;
}
