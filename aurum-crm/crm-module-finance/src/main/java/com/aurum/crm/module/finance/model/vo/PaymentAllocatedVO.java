package com.aurum.crm.module.finance.model.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 「某笔回款已被开票多少」的聚合结果，专供
 * {@code InvoicePaymentMapper#sumAllocatedByPaymentIds} 的 GROUP BY 落值。
 *
 * <p>SQL 里的 {@code SUM(...)} 必须显式起别名为 {@code paymentId} / {@code allocatedAmount}
 * （不带下划线）—— {@code application.yml} 虽开了 {@code map-underscore-to-camel-case}，
 * 但依赖它去猜聚合列的驼峰名是在两个配置之间走钢丝，显式命名更稳。
 */
@Data
public class PaymentAllocatedVO {

    private Long paymentId;

    private BigDecimal allocatedAmount;
}
