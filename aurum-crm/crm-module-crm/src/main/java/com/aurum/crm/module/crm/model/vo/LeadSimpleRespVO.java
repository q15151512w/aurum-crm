package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 线索选择器响应，只有 {@code constants/selectorRegistry.js} 里 {@code lead}
 * 选择器真正读的三列（外加前端到处都要的 {@code id}）：线索名称 / 负责人 / 联系电话。
 */
@Data
@Schema(description = "线索选择器响应")
public class LeadSimpleRespVO {

    @Schema(description = "线索ID")
    private Long id;

    @Schema(description = "线索名称")
    private String name;

    @Schema(description = "负责人姓名（跨表补值）")
    private String ownerName;

    @Schema(description = "联系电话")
    private String phone;
}
