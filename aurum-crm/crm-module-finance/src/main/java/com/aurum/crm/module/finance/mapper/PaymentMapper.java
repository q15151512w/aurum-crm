package com.aurum.crm.module.finance.mapper;

import com.aurum.crm.module.finance.model.PaymentDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 回款 Mapper。
 *
 * <p>继承 {@link BaseMapperX}，包路径在本包（{@code com.aurum.crm.**.mapper}）下，
 * 由 {@code MybatisPlusConfig} 的 {@code @MapperScan} 扫描注册。
 */
@Mapper
public interface PaymentMapper extends BaseMapperX<PaymentDO> {

    /**
     * 汇总某合同下<b>已占用合同额度</b>的回款（{@code excludeId} 用于编辑时排除自身）。
     *
     * <p>占用口径：排除<b>已驳回</b>（{@code audit_status = 3}）与<b>已作废</b>
     * （{@code status = 2}），即「待提审 / 审批中 / 已通过」都算。两个方向都不能少：
     * <ul>
     *     <li>审核中的单据若不占额度，同一笔款会被重复登记，等审批通过才发现已超额</li>
     *     <li>被驳回的单据若不释放额度，申请人补登记时会被「累计不能超合同金额」挡住
     *       —— 而那笔钱其实从未登记成功，额度被一张废单永久锁死</li>
     * </ul>
     *
     * <p><b>与 {@link #sumReceivedByContract} 是两个口径，不要合并</b>：
     * 「还能登记多少」问的是额度占用，「实际收了多少」问的是到账事实。
     * 用同一条 SQL 回答两个问题，必然有一边是错的（被驳回的单据会同时让
     * 未回款金额虚低、让补登记被误拒）。
     *
     * <p>⚠️ 绕过数据权限：未回款金额、累计回款校验都必须基于「合同的全部回款」，
     * 而非「当前登录人可见的回款」。否则他人登记的回款不参与汇总，会高估未回款、
     * 并让「累计不能超合同金额」的校验形同虚设（可被并发/多角色超登记）。
     */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT COALESCE(SUM(amount), 0) FROM fin_payment WHERE contract_id = #{contractId} AND del_flag = 0 AND audit_status != 3 AND status != 2 AND (#{excludeId} IS NULL OR payment_id <> #{excludeId})")
    BigDecimal sumCommittedByContract(@Param("contractId") Long contractId, @Param("excludeId") Long excludeId);

    /**
     * 汇总某合同下<b>已确认到账</b>的回款，用于「未回款金额」展示。
     *
     * <p>到账口径：{@code audit_status = 2 已通过} <b>且</b> {@code status = 1 已确认}。
     * 审核中 / 已驳回 / 已作废都不算 —— 钱没有真正到账。
     * 若把审核中的也算进来，列表会显示「未回款 0」，让人以为款已收齐。
     */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT COALESCE(SUM(amount), 0) FROM fin_payment WHERE contract_id = #{contractId} AND del_flag = 0 AND audit_status = 2 AND status = 1")
    BigDecimal sumReceivedByContract(@Param("contractId") Long contractId);

    /**
     * 按 ID 批量行锁读回款，供发票保存前的校验与加锁。
     *
     * <p>⚠️ <b>这里刻意<u>不</u>加 {@code @InterceptorIgnore}</b>，与
     * {@link CrmContractMiniMapper#selectByIdForUpdate} 的选择刚好相反，原因不同：
     * 合同那边校验的是全局合同总额，可见性不该介入；而这里加锁读<b>兼作归属校验</b> ——
     * 数据权限拦截器会追加 {@code owner_user_id/dept_id} 条件，于是「用户能给自己
     * 看不到的回款开票」这个越权路径被自然堵死，且不需要单独再写一次归属判断。
     *
     * <p>⚠️ 后人在此处「顺手补齐 ignore」会让归属校验一起消失。真需要绕过权限的
     * 展示型补值请用 {@link #selectByIdsNoPerm}。
     *
     * <p>{@code ORDER BY payment_id} 是并发保护的关键：删掉
     * {@code fin_invoice.uk_payment_active} 之后，「累计开票不超回款金额」退化成
     * check-then-act，两张并发保存的发票互相看不见对方未提交的明细。行锁把它们串行化，
     * 而统一按 {@code payment_id} 升序加锁，可规避共享同一批回款时经典的 AB/BA 死锁。
     *
     * <p>诚实说明其局限：InnoDB 在范围扫描下仍可能产生 gap lock 死锁，
     * 排序只是尽力而为 —— 真撞上时事务会干净回滚，不会写脏数据。
     *
     * <p>⚠️ 必须在事务中调用：{@code FOR UPDATE} 在自动提交下会立刻释放锁，等于没锁。
     *
     * @param ids 回款ID集合，调用方需保证非空（{@code IN ()} 是语法错误）
     */
    @Select("""
            <script>
            SELECT payment_id, payment_no, contract_id, customer_id, owner_user_id, dept_id,
                   amount, payment_date, payment_method, account_name, account_no, serial_no,
                   audit_status, status, remark, del_flag
              FROM fin_payment
             WHERE del_flag = 0
               AND payment_id IN <foreach collection='ids' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>
             ORDER BY payment_id
             FOR UPDATE
            </script>
            """)
    List<PaymentDO> selectByIdsForUpdate(@Param("ids") Collection<Long> ids);

    /**
     * 按 ID 批量补值（详情/编辑回显回款编号），绕过数据权限。
     *
     * <p>与 {@link #selectByIdsForUpdate} 相反，这里<b>需要</b> ignore：编辑一张挂在
     * 同事名下回款的发票时，表单若因权限读不到该回款就会显示空行，用户以为数据丢了。
     */
    @InterceptorIgnore(dataPermission = "true")
    @Select("""
            <script>
            SELECT payment_id, payment_no, contract_id, customer_id, owner_user_id, dept_id,
                   amount, payment_date, payment_method, account_name, account_no, serial_no,
                   audit_status, status, remark, del_flag
              FROM fin_payment
             WHERE del_flag = 0
               AND payment_id IN <foreach collection='ids' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>
            </script>
            """)
    List<PaymentDO> selectByIdsNoPerm(@Param("ids") Collection<Long> ids);
}
