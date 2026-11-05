package com.aurum.crm.module.crm.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 公海分配请求，对应原型「分配」弹窗的负责人选择 + 备注。
 */
@Data
@Schema(description = "公海分配")
public class CustomerPoolAssignReqVO {

    @Schema(description = "分配给的负责人ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "负责人不能为空")
    private Long ownerUserId;

    @Schema(description = "分配原因备注")
    private String reasonRemark;
}
