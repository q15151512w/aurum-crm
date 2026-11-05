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
 * 线索 DO，对应表 {@code crm_lead}。
 *
 * <p>线索是客户转化前的潜在客户。归属人 {@code owner_user_id} 为空（NULL）表示该线索在线索池中。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_lead")
public class LeadDO extends BaseDO implements Serializable {

    /** 线索ID */
    @TableId
    private Long leadId;

    /** 线索编号 */
    private String leadNo;

    /** 线索名称（公司/客户名） */
    private String leadName;

    /** 联系人姓名 */
    private String contactName;

    /** 职位 */
    private String position;

    /** 联系电话（加密） */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 微信 */
    private String wechat;

    /** 所属行业（字典 crm_industry） */
    private Integer industry;

    /** 线索来源（字典 crm_source） */
    private Integer source;

    /** 客户级别（字典 crm_customer_level，与 crm_customer.level 同一套字典） */
    private Integer level;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区县 */
    private String district;

    /** 详细地址 */
    private String address;

    /** 归属人ID（NULL=在线索池） */
    private Long ownerUserId;

    /** 归属部门ID */
    private Long deptId;

    /** 进入线索池时间 */
    private LocalDateTime enterPoolTime;

    /** 进入池原因编码（字典 crm_pool_reason） */
    private Integer enterPoolReasonCode;

    /** 进入池原因备注 */
    private String enterPoolRemark;

    /** 前归属人ID */
    private Long prevOwnerId;

    /** 下次联系时间（DATE，与商机的 expected_close_date 同日粒度） */
    private LocalDate nextContactTime;

    /** 最后跟进时间 */
    private LocalDateTime lastFollowTime;

    /** 转化时间 */
    private LocalDateTime convertTime;

    /** 转化生成的客户ID */
    private Long convertedCustomerId;

    /** 备注 */
    private String remark;
}
