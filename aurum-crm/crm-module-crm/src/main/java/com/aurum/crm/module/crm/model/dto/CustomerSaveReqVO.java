package com.aurum.crm.module.crm.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 客户新增 / 修改请求，字段对应原型「新增客户」表单。
 */
@Data
@Schema(description = "客户新增/修改")
public class CustomerSaveReqVO {

    @Schema(description = "客户名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "客户名称不能为空")
    private String customerName;

    @Schema(description = "来源线索ID（可空）")
    private Long leadId;

    @Schema(description = "所属行业（字典 crm_industry）")
    private Integer industry;

    @Schema(description = "客户级别（1重要 2普通 3非优先）")
    private Integer level;

    @Schema(description = "成交状态（1未成交 2已成交）")
    private Integer dealStatus;

    @Schema(description = "客户来源（字典 crm_source）")
    private Integer source;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "官网")
    private String website;

    @Schema(description = "省")
    private String province;

    @Schema(description = "市")
    private String city;

    @Schema(description = "区县")
    private String district;

    @Schema(description = "行政区划代码")
    private String regionCode;

    @Schema(description = "详细地址")
    private String address;

    @Schema(description = "负责人ID（为空默认当前登录人）")
    private Long ownerUserId;

    @Schema(description = "进入公海原因编码（字典 crm_pool_reason）")
    private Integer enterPoolReasonCode;

    @Schema(description = "进入公海原因备注")
    private String enterPoolRemark;

    @Schema(description = "备注")
    private String remark;
}
