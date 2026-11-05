package com.aurum.crm.module.crm.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户 DO，对应表 {@code crm_customer}。
 *
 * <p>核心资产表：客户是线索转化或直接新建的结果，
 * 归属人 {@code owner_user_id} 为空（NULL）表示该客户在公海中。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer")
public class CustomerDO extends BaseDO implements Serializable {

    /** 客户ID */
    @TableId
    private Long customerId;

    /** 客户编号 */
    private String customerNo;

    /** 客户名称 */
    private String customerName;

    /** 来源线索ID（可空，支持直接新增客户） */
    private Long leadId;

    /** 所属行业（字典 crm_industry） */
    private Integer industry;

    /** 客户级别（1重要 2普通 3非优先） */
    private Integer level;

    /** 成交状态（1未成交 2已成交，字典 crm_deal_status） */
    private Integer dealStatus;

    /** 客户来源（字典 crm_source） */
    private Integer source;

    /** 联系电话（加密） */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 官网 */
    private String website;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区县 */
    private String district;

    /** 行政区划代码 */
    private String regionCode;

    /** 详细地址 */
    private String address;

    /** 归属人ID（NULL=在公海） */
    private Long ownerUserId;

    /** 归属部门ID */
    private Long deptId;

    /** 进入公海时间 */
    private LocalDateTime enterPoolTime;

    /** 进入公海原因编码（字典 crm_pool_reason） */
    private Integer enterPoolReasonCode;

    /** 进入公海原因备注 */
    private String enterPoolRemark;

    /** 前归属人ID */
    private Long prevOwnerId;

    /**
     * 下次联系时间。
     *
     * <p>首页看板「待回访客户」的判定依据：该日期已过 且 尚未成交（{@code deal_status = 1}）。
     * 与 {@code crm_lead} / {@code crm_opportunity} 的同名列保持一致 —— 同为 {@code DATE}，
     * 因为前端用的是日期选择器、不带时间。
     */
    private LocalDate nextContactTime;

    /** 最后跟进时间（公海回收判定依据） */
    private LocalDateTime lastFollowTime;

    /** 备注 */
    private String remark;
}
