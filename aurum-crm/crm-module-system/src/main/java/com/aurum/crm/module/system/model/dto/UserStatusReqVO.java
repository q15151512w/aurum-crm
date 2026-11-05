package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 激活 / 禁用 / 操作离职（三态），对应 {@code PUT /system/user/{id}/status}。
 *
 * <p>这里的 {@code status} 是**数字**（1 正常 / 2 禁用 / 3 离职），
 * 与新增表单里的中文 {@code workStatus} / {@code accountStatus} 是两套口径 ——
 * 前端在「批量按钮」和「筛选胶囊」上都用数字，只有表单用中文。
 */
@Data
@Schema(description = "人员状态变更")
public class UserStatusReqVO {

    @Schema(description = "目标状态：1 激活 / 2 禁用 / 3 离职", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请指定目标状态")
    private Integer status;
}
