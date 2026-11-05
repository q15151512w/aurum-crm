package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 客户选择器响应，只有 {@code constants/selectorRegistry.js} 里 {@code customer}
 * 选择器真正读的四列（外加前端到处都要的 {@code id}）：客户名称 / 负责人 / 联系电话 / 级别。
 */
@Data
@Schema(description = "客户选择器响应")
public class CustomerSimpleRespVO {

    @Schema(description = "客户ID")
    private Long id;

    @Schema(description = "客户名称")
    private String name;

    @Schema(description = "负责人姓名（跨表补值）")
    private String ownerName;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "客户级别（1重要 2普通 3非优先）")
    private Integer level;
}
