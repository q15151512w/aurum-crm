package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 新增角色组。前端只有「新增」和「删除」，没有编辑 —— 所以没有 update 接口 */
@Data
@Schema(description = "新增角色组")
public class RoleGroupSaveReqVO {

    @Schema(description = "角色组名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入角色组名称")
    private String name;

    @Schema(description = "备注")
    private String remark;
}
