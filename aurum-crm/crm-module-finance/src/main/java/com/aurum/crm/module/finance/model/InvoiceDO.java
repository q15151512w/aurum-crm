package com.aurum.crm.module.finance.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 发票 DO，对应表 {@code fin_invoice}。
 *
 * <p>归属人 {@code owner_user_id} 非空，数据权限由
 * {@code DataPermissionInnerInterceptor} 统一改写（与客户模块同法）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_invoice")
public class InvoiceDO extends BaseDO {

    /** 发票ID */
    @TableId
    private Long invoiceId;

    /** 发票申请编号（系统生成） */
    private String applyNo;

    /** 实际发票号（开票后回填） */
    private String invoiceNo;

    private Long paymentId;

    /** 关联合同ID */
    private Long contractId;

    /** 关联客户ID */
    private Long customerId;

    /** 归属人ID */
    private Long ownerUserId;

    /** 归属部门ID */
    private Long deptId;

    /** 发票类型（1增值税专票 2增值税普票 3电子专票 4电子普票） */
    private Integer invoiceType;

    /**
     * 开票名称（抬头）。
     *
     * <p>以下六个字段是「客户开票信息」的<b>快照</b>：选中客户时从
     * {@code crm_customer_invoice_info} 的默认行带出、之后允许前台改动。
     * 存快照而不是每次 join 客户表，是为了让历史发票保留开票当时的抬头 ——
     * 客户后来改了名称或账号，不应该让已开出的票跟着变。
     * 列名与来源表逐字相同，故 MapStruct 转换无需 {@code @Mapping}。
     */
    private String invoiceTitle;

    /** 纳税人识别号（快照） */
    private String taxNo;

    /** 开户行（快照） */
    private String bankName;

    /** 银行账号（快照） */
    private String bankAccount;

    /** 开票地址（快照） */
    private String invoiceAddr;

    /** 开票电话（快照） */
    private String invoicePhone;

    /** 不含税金额 */
    private BigDecimal amount;

    /** 税率(%) */
    private BigDecimal taxRate;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 价税合计 */
    private BigDecimal totalAmount;

    /** 申请日期 */
    private LocalDate applyDate;

    /** 开票日期 */
    private LocalDate issueDate;

    /** 开票人(财务) */
    private Long issueUserId;

    /** 审核状态（0待提审 1审批中 2已通过 3已驳回） */
    private Integer auditStatus;

    /** 单据状态（0待开票 1已开票 2已作废） */
    private Integer status;

    /** 备注 */
    private String remark;

    private String attachmentIds;
}
