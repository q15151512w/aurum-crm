package com.aurum.crm.module.finance.mapper;

import com.aurum.crm.module.finance.model.InvoicePaymentDO;
import com.aurum.crm.module.finance.model.vo.PaymentAllocatedVO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 发票回款明细 Mapper（{@code fin_invoice_payment}）。
 *
 * <p>本表<b>不继承 {@code BaseDO}</b>、也没有 {@code @TableLogic}，所以
 * {@code BaseMapper.delete(wrapper)} 发出的是物理 DELETE —— 维护明细时
 * 按 {@code invoice_id} 整批删再重插即可，不需要手写 {@code @Delete}。
 */
@Mapper
public interface InvoicePaymentMapper extends BaseMapperX<InvoicePaymentDO> {

    /**
     * 汇总给定回款「已被开票多少」，用于计算剩余可开票余额。
     *
     * <p>算作「已占用」的口径只有一处：明细存在、发票未软删、且发票
     * {@code status != 2}（未作废）。作废发票的明细要留着做审计轨迹，所以
     * 不能靠删明细来释放额度，只能在这里排除。这个口径务必别在 Java 里手搓第二遍。
     *
     * <p>⚠️ <b>必须绕过数据权限</b>：SQL join 了白名单表 {@code fin_invoice}，
     * 拦截器会给它追加 {@code owner_user_id/dept_id} 条件。那样非「全部数据」范围的
     * 用户只统计得到自己可见的发票，剩余额度被算大 —— 症状是「界面显示还能开 3 万，
     * 一保存却报余额不足」。余额是全局事实，不随调用者身份变化。
     * 同 {@link PaymentMapper#sumCommittedByContract}。
     *
     * <p>⚠️ {@code excludeInvoiceId} 的三元写法不能省：编辑发票时要排除本发票自己的
     * 占用，否则「原样保存」都会被判超余额。但新增时传 null，写成
     * {@code i.invoice_id != #{excludeInvoiceId}} 会因 {@code != NULL} 恒为 NULL
     * 而把整表过滤光 —— 那样所有回款都会显示 100% 可用。故必须显式判 null，
     * 且用绑定参数而非 {@code ${}} 拼接。
     *
     * <p>用 {@code !=} 而不是 {@code <>}：整段是 MyBatis 的 XML script，
     * {@code <>} 会被 XML 解析器当成空标签，得写成 {@code &lt;&gt;} 才过 ——
     * 与其留个必须转义的坑，不如换个等价写法。
     *
     * @param paymentIds       待统计的回款ID，不可为空集合（{@code IN ()} 是语法错误）
     * @param excludeInvoiceId 排除的发票ID，可为 null（新增场景）
     */
    @InterceptorIgnore(dataPermission = "true")
    @Select("""
            <script>
            SELECT ip.payment_id AS paymentId, COALESCE(SUM(ip.write_off_amount), 0) AS allocatedAmount
              FROM fin_invoice_payment ip
              JOIN fin_invoice i ON i.invoice_id = ip.invoice_id
             WHERE i.del_flag = 0 AND i.status != 2
               AND ip.payment_id IN <foreach collection='paymentIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>
               AND (#{excludeInvoiceId} IS NULL OR i.invoice_id != #{excludeInvoiceId})
             GROUP BY ip.payment_id
            </script>
            """)
    List<PaymentAllocatedVO> sumAllocatedByPaymentIds(@Param("paymentIds") Collection<Long> paymentIds,
                                                      @Param("excludeInvoiceId") Long excludeInvoiceId);
}
