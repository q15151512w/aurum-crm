package com.aurum.crm.module.finance.mapper;

import com.aurum.crm.module.finance.model.dto.WriteOffPageReqVO;
import com.aurum.crm.module.finance.model.vo.WriteOffRespVO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 核销记录查询 Mapper —— 把 {@code fin_invoice_payment} 明细 join 发票与回款，平铺成核销记录。
 *
 * <p><b>物理分页</b>：{@code IPage} 分页参数 + XML script 动态过滤，避免查出全表再内存切片。
 * 只统计「有效」明细：发票未软删且未作废（{@code i.del_flag=0 AND i.status!=2}），
 * 与 {@link InvoicePaymentMapper#sumAllocatedByPaymentIds} 的占用口径一致 ——
 * 作废发票的明细留作审计轨迹，不计入当前核销记录。
 */
@Mapper
public interface WriteOffMapper {

    @InterceptorIgnore(dataPermission = "true")
    @Select("""
            <script>
            SELECT ip.id                AS id,
                   ip.invoice_id        AS invoiceId,
                   i.invoice_no         AS invoiceNo,
                   ip.payment_id        AS paymentId,
                   p.payment_no         AS paymentNo,
                   p.customer_id        AS customerId,
                   p.contract_id        AS contractId,
                   ip.write_off_amount  AS writeOffAmount,
                   p.payment_date       AS paymentDate,
                   ip.create_time       AS createTime
              FROM fin_invoice_payment ip
              JOIN fin_invoice i ON i.invoice_id = ip.invoice_id
              JOIN fin_payment  p ON p.payment_id  = ip.payment_id
             WHERE i.del_flag = 0 AND i.status != 2
               <if test="q.invoiceNo != null and q.invoiceNo != ''">
               AND i.invoice_no LIKE CONCAT('%', #{q.invoiceNo}, '%')
               </if>
               <if test="q.paymentNo != null and q.paymentNo != ''">
               AND p.payment_no LIKE CONCAT('%', #{q.paymentNo}, '%')
               </if>
               <if test="q.customerId != null">
               AND p.customer_id = #{q.customerId}
               </if>
             ORDER BY ip.create_time DESC
            </script>
            """)
    IPage<WriteOffRespVO> selectWriteOffPage(@Param("page") IPage<?> page, @Param("q") WriteOffPageReqVO q);
}
