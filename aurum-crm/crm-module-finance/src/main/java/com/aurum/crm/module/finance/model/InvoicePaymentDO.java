package com.aurum.crm.module.finance.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发票回款明细 DO，对应表 {@code fin_invoice_payment}。
 *
 * <p>这是「一票多回款」的<b>唯一事实来源</b>：一张发票挂了哪些回款、各开多少钱，
 * 只认这张表。{@code fin_invoice.payment_id} 只是去规范化的「主回款」，供展示与索引。
 *
 * <p>⚠️ <b>刻意不继承 {@code BaseDO}</b>：本表没有 {@code del_flag}、也没有
 * {@code update_by/update_time}。继承了 MyBatis-Plus 就会给 update 语句里塞
 * {@code update_by}，直接抛 {@code Unknown column 'update_by' in 'field list'}。
 * 明细的维护方式是「按 invoice_id 整批删掉再插」——不需要软删，也就不需要那两个列。
 *
 * <p>同理，本表无 {@code @TableField(fill = ...)} 的自动填充，{@code createBy} /
 * {@code createTime} 必须由调用方显式赋值（{@code insertFill} 只对带注解的字段生效）。
 */
@Data
@TableName("fin_invoice_payment")
public class InvoicePaymentDO {

    /** 主键 */
    @TableId
    private Long id;

    /** 发票ID */
    private Long invoiceId;

    /** 回款ID */
    private Long paymentId;

    /** 本次开票金额（含税，与 fin_payment.amount 同口径） */
    private BigDecimal writeOffAmount;

    /** 创建人ID */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
