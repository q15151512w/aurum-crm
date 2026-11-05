package com.aurum.crm.module.crm.model;

import lombok.Getter;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 合同状态，对应 {@code crm_contract.status}。
 *
 * <p><b>与 {@link ContractAuditStatus} 的区别</b>：这个列描述的是**合同本身的履约状态**
 * （草稿 / 已生效 / 已作废 / 已到期），审核态是另一列。两个列的名字很像，别搞混 ——
 * 前端历史上就把审核态挂在了 {@code status} 这个名字下，详见 {@link ContractAuditStatus} 的注释。
 *
 * <p>转换只发生在 {@link com.aurum.crm.module.crm.model.convert.ContractConvert} 一处，
 * 接口对外统一是中文标签，与 {@link OpportunityStage} / {@link QuotationStatus} 同一套做法。
 */
@Getter
public enum ContractStatus {

    /** 草稿 */
    DRAFT(0, "草稿"),

    /** 已生效 */
    EFFECTIVE(1, "已生效"),

    /** 已作废 */
    VOIDED(2, "已作废"),

    /** 已到期 */
    EXPIRED(3, "已到期");

    private final Integer code;
    private final String label;

    ContractStatus(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 码值 → 枚举，未知码值返回 {@code null}（不抛异常）。
     *
     * <p>读路径上不能抛：库里若有历史脏码值，一条脏数据不该让整个列表接口 500。
     */
    public static ContractStatus ofCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(s -> s.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /** 中文标签 → 枚举，未知标签返回 {@code null}（不抛异常，由调用方决定怎么报错） */
    public static ContractStatus ofLabel(String label) {
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
        ContractStatus status = ofCode(code);
        return status == null ? null : status.label;
    }

    /** 合法标签的展示串，只用于拼错误提示 */
    public static String labelOptions() {
        return Arrays.stream(values())
                .map(ContractStatus::getLabel)
                .collect(Collectors.joining(" / "));
    }
}
