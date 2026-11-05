package com.aurum.crm.module.finance.model;

import java.util.Map;

/**
 * 财务模块码值 → 中文标签，专供导出（Excel 列要落中文）。
 *
 * <p>finance 模块不依赖 crm，无法复用 {@code DictLabels}，故本地维护一份
 * finance 专属字典（回款方式 / 发票类型 / 审核状态 / 单据状态），
 * 标签来源为 {@code sql/crm.sql} 的 {@code sys_dict_data}。未知码值返回 {@code null}，
 * 调用方兜底空串，不会因脏码值中断导出。
 *
 * <p><b>为什么不查字典服务</b>：字典服务已落地（{@code GET /system/dict-data/batch}），
 * 但导出路径刻意不查库 —— 一次导出几万行，逐行查字典既慢，又会把
 * 「库里有条脏码值」升级成「整张表导不出来」。这与 {@code DictLabels} 的取舍一致。
 *
 * <p>⚠️ 代价是这份表会与 {@code sys_dict_data} 漂移。**改字典种子时记得回来对一遍这里。**
 */
public final class FinanceDictLabels {

    /** 回款方式 */
    private static final Map<Integer, String> PAYMENT_METHOD = Map.of(
            1, "银行转账", 2, "现金", 3, "支票", 4, "在线支付", 5, "其他");

    /** 发票类型 */
    private static final Map<Integer, String> INVOICE_TYPE = Map.of(
            1, "增值税专票", 2, "增值税普票", 3, "电子专票", 4, "电子普票");

    /** 审核状态（回款/发票共用） */
    private static final Map<Integer, String> AUDIT_STATUS = Map.of(
            0, "待提审", 1, "审批中", 2, "已通过", 3, "已驳回");

    /** 回款单据状态 */
    private static final Map<Integer, String> PAYMENT_STATUS = Map.of(
            0, "待确认", 1, "已确认", 2, "已作废");

    /** 发票单据状态 */
    private static final Map<Integer, String> INVOICE_STATUS = Map.of(
            0, "待开票", 1, "已开票", 2, "已作废");

    private FinanceDictLabels() {
    }

    private static String get(Map<Integer, String> map, Integer code) {
        if (code == null) {
            return null;
        }
        return map.get(code);
    }

    public static String paymentMethod(Integer code) {
        return get(PAYMENT_METHOD, code);
    }

    public static String invoiceType(Integer code) {
        return get(INVOICE_TYPE, code);
    }

    public static String auditStatus(Integer code) {
        return get(AUDIT_STATUS, code);
    }

    public static String paymentStatus(Integer code) {
        return get(PAYMENT_STATUS, code);
    }

    public static String invoiceStatus(Integer code) {
        return get(INVOICE_STATUS, code);
    }
}
