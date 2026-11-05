package com.aurum.crm.module.finance.mapper;

import com.aurum.crm.module.finance.model.InvoiceDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发票 Mapper。
 *
 * <p>原先的 {@code countActiveByPayment} 已随数据模型调整删除：那是在
 * {@code uk_payment_active} 一对一约束下做的「一笔回款只能开一张票」判断，
 * 现在一笔回款可被多张发票分摊，约束改为「累计开票金额不超回款金额」，
 * 统一由 {@link InvoicePaymentMapper#sumAllocatedByPaymentIds} 承担。
 */
@Mapper
public interface InvoiceMapper extends BaseMapperX<InvoiceDO> {
}
