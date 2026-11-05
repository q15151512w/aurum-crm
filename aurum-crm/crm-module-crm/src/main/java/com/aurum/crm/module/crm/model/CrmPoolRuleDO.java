package com.aurum.crm.module.crm.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 公海规则配置 DO，对应表 {@code crm_pool_rule}。
 *
 * <p><b>用途</b>：领取上限（{@code claim_limit}）与回收周期（{@code recycle_days}）等
 * 公海/线索池运行参数的配置来源。领取上限在执行 {@code claim} 时读取，
 * 按 {@code pool_type}（1 线索池 / 2 客户池）匹配启用的规则。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_pool_rule")
public class CrmPoolRuleDO extends BaseDO {

    /** 规则 ID */
    @TableId
    private Long ruleId;

    /** 池类型：1 线索池 / 2 客户池 */
    private Integer poolType;

    /** 规则名称 */
    private String ruleName;

    /** 未跟进回收天数（0 表示不回收） */
    private Integer recycleDays;

    /** 领取上限数量（0 表示不限） */
    private Integer claimLimit;

    /** 领取上限周期：1 按日 / 2 按周 / 3 按月 */
    private Integer claimCycle;

    /** 回收前提醒天数 */
    private Integer notifyDays;

    /** 是否启用：0 禁用 / 1 启用 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}
