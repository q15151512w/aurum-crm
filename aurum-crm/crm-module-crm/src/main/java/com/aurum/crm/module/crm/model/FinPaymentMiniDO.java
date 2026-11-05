package com.aurum.crm.module.crm.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 回款单精简投影，只取首页看板统计所需的几列。
 *
 * <h3>为什么放在 crm 模块而不是建 finance 模块</h3>
 *
 * <p>{@code crm-module-finance} 目前是**空模块**（回款登记、发票登记、财务审核、开票都还没做），
 * 但 {@code fin_payment} / {@code fin_invoice} 两张表的**结构和演示数据都已经有了**。
 * 首页看板的「待财务审核」「已收款金额」需要读它们 —— 为此建一整个 finance 模块不划算，
 * 所以照 {@code SysUserMiniDO} / {@code CustomerMiniDO} 的做法做一个精简投影，
 * **只用于看板统计，不进入 finance 的 CRUD 链路**。
 *
 * <h3>⚠️ 三个必须注意的点</h3>
 *
 * <ol>
 *     <li><b>不继承 {@code BaseDO}</b>：那是给「本模块自己的业务表」用的，
 *         会带上 {@code @TableLogic} 与审计字段自动填充。投影别人的表时继承它，
 *         会让 {@code del_flag} 的语义变得不可控。代价是<b>查询要自己写
 *         {@code del_flag = 0} 条件</b> —— 所以这里显式声明了 {@code delFlag}。</li>
 *     <li><b>数据权限会自动生效</b>：{@code fin_payment} 在
 *         {@code DataPermissionInnerInterceptor} 的 {@code SCOPED_TABLES} 白名单里，
 *         用 wrapper 构造的聚合查询会被自动追加行级条件，不需要额外写。</li>
 *     <li>只声明用得到的列 —— 这既是省流量的意思，也是「不要越界」的意思：
 *         这个 DO 不该被拿去当 finance 模块的实体用。</li>
 * </ol>
 */
@Data
@TableName("fin_payment")
public class FinPaymentMiniDO implements Serializable {

    /** 回款ID */
    @TableId
    private Long paymentId;

    /** 回款金额 */
    private BigDecimal amount;

    /** 审核状态（0待提审 1审批中 2已通过 3已驳回） */
    private Integer auditStatus;

    /** 单据状态（0待确认 1已确认 2已作废） */
    private Integer status;

    /** 创建时间（看板的区间统计按它过滤） */
    private LocalDateTime createTime;

    /** 删除标志。⚠️ 不继承 BaseDO 就没有 @TableLogic，查询必须自己带上它 */
    private Integer delFlag;
}
