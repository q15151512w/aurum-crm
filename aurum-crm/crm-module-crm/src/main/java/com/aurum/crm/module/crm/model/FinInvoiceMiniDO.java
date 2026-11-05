package com.aurum.crm.module.crm.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发票精简投影，只取首页看板统计所需的几列。
 *
 * <p>与 {@link FinPaymentMiniDO} 同一套定位与注意事项（不继承 BaseDO、
 * 数据权限自动生效、只声明用得到的列），理由见那边的类注释。
 */
@Data
@TableName("fin_invoice")
public class FinInvoiceMiniDO implements Serializable {

    /** 发票ID */
    @TableId
    private Long invoiceId;

    /**
     * 价税合计（含税总额）。
     *
     * <p>看板的「已开票金额」用这一列，不是 {@code amount} ——
     * DDL 里 {@code amount} 是**不含税**金额、{@code tax_amount} 是税额、
     * {@code total_amount} 才是价税合计（《02-建表文档》3.6 的口径）。
     */
    private BigDecimal totalAmount;

    /** 审核状态（0待提审 1审批中 2已通过 3已驳回） */
    private Integer auditStatus;

    /** 单据状态（0待开票 1已开票 2已作废） */
    private Integer status;

    /** 创建时间（看板的区间统计按它过滤） */
    private LocalDateTime createTime;

    /** 删除标志。⚠️ 不继承 BaseDO 就没有 @TableLogic，查询必须自己带上它 */
    private Integer delFlag;
}
