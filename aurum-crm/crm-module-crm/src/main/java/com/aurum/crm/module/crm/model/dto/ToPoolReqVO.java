package com.aurum.crm.module.crm.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 放入公海请求，对应原型「放入公海」弹窗（原因 + 备注）。
 */
@Data
@Schema(description = "放入公海")
public class ToPoolReqVO {

    @Schema(description = "进入公海原因编码（字典 crm_pool_reason）")
    private Integer reasonCode;

    @Schema(description = "进入公海原因备注")
    private String reasonRemark;
}
