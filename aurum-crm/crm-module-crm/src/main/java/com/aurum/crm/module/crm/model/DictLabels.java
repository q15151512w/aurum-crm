package com.aurum.crm.module.crm.model;

import java.util.Map;

/**
 * 码值 → 中文标签的静态映射，专供导出（Excel 列要落中文）使用。
 *
 * <p>标签来源是 {@code sql/crm.sql} 里 {@code sys_dict_data} 的初始化数据，
 * 与 DDL 注释一致（source 官网1…其他7、industry 制造业1…其他8、level 重要1…非优先3、
 * dealStatus 未成交1…已成交2、pool_reason 长期未跟进1…客户失联5 等）。
 *
 * <p><b>审批流那几个码表是个例外</b>：{@code approvalApproveType} / {@code approverType} /
 * {@code approvalInstanceStatus} / {@code approvalTaskStatus} / {@code approveResult} 的取值
 * 不来自 {@code sys_dict_data}，而是 {@code sys_approval_node} / {@code sys_approval_instance} /
 * {@code sys_approval_task} 的 DDL 注释（见 {@code sql/crm.sql} 第 17~20 张表）。
 * 放在这里是因为用途相同：**码值 → 中文标签**，且读路径都不能因为一条脏码值而 500。
 *
 * <p><b>为什么用静态表而不是查 {@code sys_dict_data}</b>：这是**刻意的选择，不是没做**
 * —— 字典服务其实早已落地（{@code GET /system/dict-data/batch}，见 {@code DictController}），
 * 但导出路径不查库：一次导出要落几万行，每行都去查一次字典既慢，又会把
 * 「库里有条脏码值」升级成「整张表导不出来」。而且本表覆盖的审批流状态等字段
 * **根本没有对应的字典行**，本来就换不成查库。所以这里保持静态。
 * 未知码值返回 {@code null}，由调用方兜底成空串（导出不因一条脏码值而中断）。
 *
 * <p>⚠️ 代价是这份表会与 {@code sql/crm.sql} 的 {@code sys_dict_data} 漂移 ——
 * {@code POOL_REASON} 就曾经少了第 5 项。**改字典种子时记得回来对一遍这里。**
 */
public final class DictLabels {

    /** 客户/线索来源 */
    private static final Map<Integer, String> SOURCE = Map.of(
            1, "官网", 2, "电话", 3, "展会", 4, "转介绍", 5, "广告", 6, "地推", 7, "其他");

    /** 所属行业 */
    private static final Map<Integer, String> INDUSTRY = Map.of(
            1, "制造业", 2, "互联网", 3, "金融", 4, "教育", 5, "医疗", 6, "政府", 7, "零售", 8, "其他");

    /** 客户级别 */
    private static final Map<Integer, String> LEVEL = Map.of(
            1, "重要", 2, "普通", 3, "非优先");

    /** 成交状态 */
    private static final Map<Integer, String> DEAL_STATUS = Map.of(
            1, "未成交", 2, "已成交");

    /** 进入公海/池原因（5 项，与 {@code sys_dict_data.crm_pool_reason} 对齐） */
    private static final Map<Integer, String> POOL_REASON = Map.of(
            1, "长期未跟进", 2, "主动放弃", 3, "管理员操作", 4, "规则自动回收", 5, "客户失联");

    /** 合同类型 */
    private static final Map<Integer, String> CONTRACT_TYPE = Map.of(
            1, "销售合同", 2, "框架合同", 3, "补充协议");

    /** 审核状态（合同/回款/发票共用） */
    private static final Map<Integer, String> AUDIT_STATUS = Map.of(
            0, "待提审", 1, "审批中", 2, "已通过", 3, "已驳回");

    /** 商机销售阶段（crm_opportunity.stage） */
    private static final Map<Integer, String> STAGE = Map.of(
            1, "初步接洽", 2, "需求确认", 3, "方案报价", 4, "商务谈判", 5, "赢单", 6, "输单");

    /** 合同状态（crm_contract.status） */
    private static final Map<Integer, String> CONTRACT_STATUS = Map.of(
            0, "草稿", 1, "已生效", 2, "已作废", 3, "已到期");

    /** 回款方式 */
    private static final Map<Integer, String> PAYMENT_METHOD = Map.of(
            1, "银行转账", 2, "现金", 3, "支票", 4, "在线支付", 5, "其他");

    /** 发票类型 */
    private static final Map<Integer, String> INVOICE_TYPE = Map.of(
            1, "增值税专票", 2, "增值税普票", 3, "电子专票", 4, "电子普票");

    /** 回款单据状态（0待确认 1已确认 2已作废） */
    private static final Map<Integer, String> PAYMENT_STATUS = Map.of(
            0, "待确认", 1, "已确认", 2, "已作废");

    /** 发票单据状态（0待开票 1已开票 2已作废） */
    private static final Map<Integer, String> INVOICE_STATUS = Map.of(
            0, "待开票", 1, "已开票", 2, "已作废");

    // ==================== 审批流（sys_approval_*） ====================
    //
    // 这五张表不是字典表，取值来自 DDL 注释；集中在这里的理由见类注释。

    /** 节点审批方式 sys_approval_node.approve_type（1或签 2会签 3依次） */
    private static final Map<Integer, String> APPROVE_TYPE = Map.of(
            1, "或签", 2, "会签", 3, "依次");

    /** 节点审批人类型 sys_approval_node.approver_type（1指定用户…5发起人本人） */
    private static final Map<Integer, String> APPROVER_TYPE = Map.of(
            1, "指定用户", 2, "指定角色", 3, "部门主管", 4, "发起人直属上级", 5, "发起人本人");

    /** 审批实例状态 sys_approval_instance.status（0审批中 1已通过 2已驳回 3已撤销） */
    private static final Map<Integer, String> APPROVAL_INSTANCE_STATUS = Map.of(
            0, "审批中", 1, "已通过", 2, "已驳回", 3, "已撤销");

    /** 审批任务状态 sys_approval_task.task_status（0待办 1已办 2已跳过） */
    private static final Map<Integer, String> APPROVAL_TASK_STATUS = Map.of(
            0, "待办", 1, "已办", 2, "已跳过");

    /** 审批结果 sys_approval_task.approve_result（1通过 2驳回 3撤销） */
    private static final Map<Integer, String> APPROVE_RESULT = Map.of(
            1, "通过", 2, "驳回", 3, "撤销");

    private DictLabels() {
    }

    /** 码值 → 标签，未知返回 {@code null} */
    private static String get(Map<Integer, String> map, Integer code) {
        if (code == null) {
            return null;
        }
        return map.get(code);
    }

    public static String source(Integer code) {
        return get(SOURCE, code);
    }

    public static String industry(Integer code) {
        return get(INDUSTRY, code);
    }

    public static String level(Integer code) {
        return get(LEVEL, code);
    }

    public static String dealStatus(Integer code) {
        return get(DEAL_STATUS, code);
    }

    public static String poolReason(Integer code) {
        return get(POOL_REASON, code);
    }

    public static String contractType(Integer code) {
        return get(CONTRACT_TYPE, code);
    }

    public static String auditStatus(Integer code) {
        return get(AUDIT_STATUS, code);
    }

    public static String stage(Integer code) {
        return get(STAGE, code);
    }

    public static String contractStatus(Integer code) {
        return get(CONTRACT_STATUS, code);
    }

    public static String paymentMethod(Integer code) {
        return get(PAYMENT_METHOD, code);
    }

    public static String invoiceType(Integer code) {
        return get(INVOICE_TYPE, code);
    }

    public static String paymentStatus(Integer code) {
        return get(PAYMENT_STATUS, code);
    }

    public static String invoiceStatus(Integer code) {
        return get(INVOICE_STATUS, code);
    }

    public static String approveType(Integer code) {
        return get(APPROVE_TYPE, code);
    }

    public static String approverType(Integer code) {
        return get(APPROVER_TYPE, code);
    }

    public static String approvalInstanceStatus(Integer code) {
        return get(APPROVAL_INSTANCE_STATUS, code);
    }

    public static String approvalTaskStatus(Integer code) {
        return get(APPROVAL_TASK_STATUS, code);
    }

    public static String approveResult(Integer code) {
        return get(APPROVE_RESULT, code);
    }
}
