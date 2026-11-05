package com.aurum.crm.module.crm.model;

import lombok.Getter;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 合同审核状态，对应 {@code crm_contract.audit_status}。
 *
 * <p><b>为什么需要这个枚举</b>：列是 {@code TINYINT}（0 待提审 / 1 审批中 / 2 已通过 / 3 已驳回），
 * 而前端列表与详情页都是按**中文标签**渲染的 —— {@code contract/index.vue} 的
 * {@code STATUS_TYPE} 配色表、{@code detail.vue} 的 {@code statusType()} 都以
 * 「未提交 / 审核中 / 审核通过 / 审核拒绝」为键。转换只发生在
 * {@link com.aurum.crm.module.crm.model.convert.ContractConvert} 一处，
 * 接口对外统一是中文标签，与 {@link OpportunityStage} / {@link QuotationStatus} 同一套做法。
 *
 * <p>⚠️ <b>前端那一列曾经叫 {@code status}，展示的却是这里的审核态</b>。
 * {@code crm_contract} 有**两个**状态列：{@code audit_status}（本枚举）与
 * {@code status}（见 {@link ContractStatus}），前端当时只做了前者却挂了后者的名字。
 * 本次字段对齐把前端列名改为 {@code auditStatus}，语义不变、名实相符。
 */
@Getter
public enum ContractAuditStatus {

    /** 待提审 —— 前端标签「未提交」 */
    DRAFT(0, "未提交"),

    /** 审批中 */
    AUDITING(1, "审核中"),

    /** 已通过 */
    APPROVED(2, "审核通过"),

    /** 已驳回 */
    REJECTED(3, "审核拒绝");

    private final Integer code;
    private final String label;

    ContractAuditStatus(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 码值 → 枚举，未知码值返回 {@code null}（不抛异常）。
     *
     * <p>读路径上不能抛：库里若有历史脏码值，一条脏数据不该让整个列表接口 500。
     * 转换器会把 {@code null} 原样透出，前端用 {@code dash()} 兜底成 {@code --}。
     */
    public static ContractAuditStatus ofCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(s -> s.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 中文标签 → 枚举，未知标签返回 {@code null}（不抛异常，由调用方决定怎么报错）。
     *
     * <p>做「去空格 + 大小写无关」的宽松匹配：前端下拉给的是精确标签，
     * 但 curl / Knife4j 联调时手敲带尾空格的情况很常见。
     */
    public static ContractAuditStatus ofLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(s -> s.label.toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    /** 码值 → 中文标签，未知码值返回 {@code null}（供 MapStruct 的 {@code qualifiedByName} 调用） */
    public static String labelOf(Integer code) {
        ContractAuditStatus status = ofCode(code);
        return status == null ? null : status.label;
    }

    /** 合法标签的展示串，只用于拼错误提示 */
    public static String labelOptions() {
        return Arrays.stream(values())
                .map(ContractAuditStatus::getLabel)
                .collect(Collectors.joining(" / "));
    }
}
