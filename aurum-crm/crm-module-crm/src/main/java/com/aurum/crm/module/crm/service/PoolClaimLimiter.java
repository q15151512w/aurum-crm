package com.aurum.crm.module.crm.service;

import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.mapper.CrmPoolRuleMapper;
import com.aurum.crm.module.crm.mapper.OwnerChangeLogMapper;
import com.aurum.crm.module.crm.model.CrmPoolRuleDO;
import com.aurum.crm.module.crm.model.OwnerChangeLogDO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 公海 / 线索池「每日领取上限」执行器。
 *
 * <p>{@code claim} 里的原子 CAS 只解决「两人抢同一条」，解决不了「同一个人
 * 无限领取」。本组件读取 {@code crm_pool_rule.claim_limit}（0 = 不限），
 * 统计当前用户在**当前周期**内的领取次数（{@code crm_owner_change_log.action = 2}），
 * 超限抛 {@code 2601}。
 *
 * <p><b>计数口径</b>：按 {@code toOwnerId = 当前用户} 且 {@code action = 2}（领取）。
 * 周期起点按 {@code claim_cycle}：1 今日零点 / 2 本周一零点 / 3 本月 1 号零点。
 * 规则未配置或 {@code claim_limit = 0} 时**不拦截**（与「0 表示不限」语义一致）。
 */
@Component
public class PoolClaimLimiter {

    /** 领取动作码（{@link OwnerChangeLogDO#getAction()}） */
    private static final int ACTION_CLAIM = 2;

    @Resource
    private CrmPoolRuleMapper crmPoolRuleMapper;
    @Resource
    private OwnerChangeLogMapper ownerChangeLogMapper;

    /**
     * 在执行领取前校验当前用户是否已达周期领取上限。
     *
     * @param poolType 1 线索池 / 2 客户池（与 {@code crm_pool_rule.pool_type} 对齐）
     * @param bizType  归属流转记录的业务类型（1 线索 / 2 客户）
     * @param userId   领取人
     */
    public void checkBeforeClaim(int poolType, int bizType, Long userId) {
        CrmPoolRuleDO rule = crmPoolRuleMapper.selectOne(
                Wrappers.<CrmPoolRuleDO>lambdaQuery()
                        .eq(CrmPoolRuleDO::getPoolType, poolType)
                        .eq(CrmPoolRuleDO::getEnabled, 1)
                        .last("LIMIT 1"));
        if (rule == null || rule.getClaimLimit() == null || rule.getClaimLimit() <= 0) {
            return; // 未配置或 0 = 不限
        }

        LocalDateTime cycleStart = cycleStart(rule.getClaimCycle() == null ? 1 : rule.getClaimCycle());
        long claimed = ownerChangeLogMapper.selectCount(
                Wrappers.<OwnerChangeLogDO>lambdaQuery()
                        .eq(OwnerChangeLogDO::getBizType, bizType)
                        .eq(OwnerChangeLogDO::getAction, ACTION_CLAIM)
                        .eq(OwnerChangeLogDO::getToOwnerId, userId)
                        .ge(OwnerChangeLogDO::getCreateTime, cycleStart));
        if (claimed >= rule.getClaimLimit()) {
            throw new ServiceException(ErrorCode.POOL_CLAIM_LIMIT,
                    "已达周期领取上限（" + rule.getClaimLimit() + " 条）");
        }
    }

    /** 周期起点：1 今日零点 / 2 本周一零点 / 3 本月 1 号零点（其余按日兜底） */
    private LocalDateTime cycleStart(int cycle) {
        LocalDate today = LocalDate.now();
        return switch (cycle) {
            case 2 -> today.with(DayOfWeek.MONDAY).atStartOfDay();
            case 3 -> today.withDayOfMonth(1).atStartOfDay();
            default -> today.atStartOfDay();
        };
    }
}
