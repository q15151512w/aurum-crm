package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户列表 / 详情响应，列与原型表格对齐：
 * 客户名称 | 负责人 | 联系电话 | 邮箱 | 所属行业 | 客户来源 | 创建时间 | 操作。
 */
@Data
@Schema(description = "客户响应")
public class CustomerRespVO {

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "客户ID（前端列表主键别名，与 customerId 相同）")
    private Long id;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "客户名称（前端列表列 prop，与 customerName 相同）")
    private String name;

    @Schema(description = "客户编号")
    private String customerNo;

    @Schema(description = "来源线索ID")
    private Long leadId;

    @Schema(description = "所属行业（字典 crm_industry）")
    private Integer industry;

    @Schema(description = "客户级别（1重要 2普通 3非优先）")
    private Integer level;

    @Schema(description = "成交状态（1未成交 2已成交）")
    private Integer dealStatus;

    @Schema(description = "客户来源（字典 crm_source）")
    private Integer source;

    @Schema(description = "联系电话（非授权角色脱敏）")
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

    /**
     * 省市区拼接串（派生字段，前端列表「地区」列 + 编辑弹窗回填级联选择器）。
     *
     * <p>库里没有这一列，由 {@code CustomerConvert} 的 {@code @AfterMapping} 从上面三段拼出来。
     */
    @Schema(description = "地区拼接串，如 广东省广州市天河区")
    private String region;

    @Schema(description = "行政区划代码")
    private String regionCode;

    @Schema(description = "详细地址")
    private String address;

    @Schema(description = "负责人ID（NULL=在公海）")
    private Long ownerUserId;

    @Schema(description = "负责人姓名")
    private String ownerName;

    @Schema(description = "当前登录人是否已关注该客户")
    private Boolean starred;

    @Schema(description = "归属部门ID")
    private Long deptId;

    @Schema(description = "进入公海时间")
    private LocalDateTime enterPoolTime;

    @Schema(description = "进入公海原因编码（字典 crm_pool_reason）")
    private Integer enterPoolReasonCode;

    @Schema(description = "进入公海原因备注")
    private String enterPoolRemark;

    @Schema(description = "前归属人ID")
    private Long prevOwnerId;

    @Schema(description = "最后跟进时间")
    private LocalDateTime lastFollowTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
