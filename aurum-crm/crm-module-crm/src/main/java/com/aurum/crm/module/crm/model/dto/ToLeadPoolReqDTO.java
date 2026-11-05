package com.aurum.crm.module.crm.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 放入线索池请求，对应「放入线索池」弹窗（原因 + 备注）。
 * 字段语义与 {@code ToPoolReqDTO} 一致。
 */
@Data
@Schema(description = "放入线索池")
public class ToLeadPoolReqDTO {

    @Schema(description = "进入线索池原因编码（字典 crm_pool_reason）")
    private Integer reasonCode;

    @Schema(description = "进入线索池原因备注")
    private String reasonRemark;
}
