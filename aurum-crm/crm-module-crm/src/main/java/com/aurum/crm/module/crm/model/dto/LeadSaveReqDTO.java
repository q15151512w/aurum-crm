package com.aurum.crm.module.crm.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 线索新增 / 修改请求，字段对应原型「新增线索」表单。
 */
@Data
@Schema(description = "线索新增/修改")
public class LeadSaveReqDTO {

    @Schema(description = "线索名称（公司/客户名）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "线索名称不能为空")
    private String leadName;

    @Schema(description = "联系人姓名")
    private String contactName;

    @Schema(description = "职位")
    private String position;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "微信")
    private String wechat;

    @Schema(description = "所属行业（字典 crm_industry）")
    private Integer industry;

    @Schema(description = "线索来源（字典 crm_source）")
    private Integer source;

    @Schema(description = "客户级别（字典 crm_customer_level：1重要 2普通 3非优先）")
    private Integer level;

    @Schema(description = "省")
    private String province;

    @Schema(description = "市")
    private String city;

    @Schema(description = "区县")
    private String district;

    @Schema(description = "详细地址")
    private String address;

    /**
     * 下次联系时间，格式 {@code yyyy-MM-dd}。
     *
     * <p>刻意声明成 {@code String} 而不是 {@code LocalDate}：前端日期控件清空后发的是
     * **空字符串**，而 Jackson 会把 {@code ""} 交给 {@code LocalDate.parse} 直接抛
     * {@code HttpMessageNotReadableException}（表现为「请求体格式不正确」）。
     * 由 Service 用宽容解析处理：空白 → {@code null}，非法 → 400 带明确文案。
     *
     * <p>与 {@code OpportunitySaveReqVO.expectDealTime} 同一套写法。
     */
    @Schema(description = "下次联系时间（yyyy-MM-dd，可空）")
    private String nextContactTime;

    @Schema(description = "负责人ID（为空默认当前登录人）")
    private Long ownerUserId;



    @Schema(description = "进入线索池原因编码（字典 crm_pool_reason）")
    private Integer enterPoolReasonCode;

    @Schema(description = "进入线索池原因备注")
    private String enterPoolRemark;

    @Schema(description = "备注")
    private String remark;
}
