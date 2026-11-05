package com.aurum.crm.module.bpm.service;

import com.aurum.crm.module.bpm.model.SysApprovalInstanceDO;
import lombok.Getter;

/**
 * 一次审批处理对实例的推进结果，供调用方据此落业务对象自身状态（如合同 audit_status）。
 *
 * <p>引擎不反写业务对象 —— 调用方拿到 {@link #getOutcome()} 决定如何推进业务状态：
 * {@code APPROVED / REJECTED} 为终态，{@code RUNNING} 表示实例仍在流转。
 */
@Getter
public class ApprovalResult {

    /** 审批结果：RUNNING / APPROVED / REJECTED */
    public enum Outcome { RUNNING, APPROVED, REJECTED }

    private final Outcome outcome;
    private final SysApprovalInstanceDO instance;

    private ApprovalResult(Outcome outcome, SysApprovalInstanceDO instance) {
        this.outcome = outcome;
        this.instance = instance;
    }

    public static ApprovalResult running(SysApprovalInstanceDO instance) {
        return new ApprovalResult(Outcome.RUNNING, instance);
    }

    public static ApprovalResult approved(SysApprovalInstanceDO instance) {
        return new ApprovalResult(Outcome.APPROVED, instance);
    }

    public static ApprovalResult rejected(SysApprovalInstanceDO instance) {
        return new ApprovalResult(Outcome.REJECTED, instance);
    }
}
