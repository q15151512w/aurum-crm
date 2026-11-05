package com.aurum.crm.module.crm.model;

import java.util.Set;

/**
 * 关注业务类型常量，对应 {@code crm_star.biz_type}。
 *
 * <p>取值顺序与《05-接口文档》9.3「我关注的」的 {@code tab} 参数一致，
 * 前三个（线索 / 客户 / 商机）与 {@code crm_follow_up}、{@code crm_owner_change_log}
 * 的 {@code biz_type} 约定保持相同语义 —— 同一张业务表在不同关联表里编号不要漂移。
 *
 * <p>用常量持有类而不是枚举：本仓库除 {@code ErrorCode} 外没有枚举，
 * 惯例见 {@code crm-module-system} 的 {@code AuthConstants}。
 */
public final class StarBizType {

    /** 线索 */
    public static final int LEAD = 1;

    /** 客户 */
    public static final int CUSTOMER = 2;

    /** 商机 */
    public static final int OPPORTUNITY = 3;

    /** 报价单 */
    public static final int QUOTATION = 4;

    /** 合同 */
    public static final int CONTRACT = 5;

    /** 回款 */
    public static final int PAYMENT = 6;

    /** 发票 */
    public static final int INVOICE = 7;

    private static final Set<Integer> SUPPORTED =
            Set.of(LEAD, CUSTOMER, OPPORTUNITY, QUOTATION, CONTRACT, PAYMENT, INVOICE);

    private StarBizType() {
    }

    /** 是否为已登记的业务类型，未登记的取值一律拒绝，避免脏数据写进通用表 */
    public static boolean isSupported(Integer bizType) {
        return bizType != null && SUPPORTED.contains(bizType);
    }
}
