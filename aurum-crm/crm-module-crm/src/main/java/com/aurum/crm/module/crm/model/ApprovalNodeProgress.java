package com.aurum.crm.module.crm.model;

import lombok.Getter;

import java.util.Arrays;

/**
 * 审批**节点**的展示进度（合同审批进度接口用）。
 *
 * <p><b>它不是数据库里的列</b>：库里只有 {@code sys_approval_node}（模板节点，没有状态）
 * 与 {@code sys_approval_task}（每个审批人的任务，有 {@code task_status} / {@code approve_result}）。
 * 本枚举是「把某节点的全部任务归并成一句话」的展示口径，归并规则见
 * {@code ContractServiceImpl#nodeProgress}，顺序即优先级：
 *
 * <pre>
 *   无任务                        → 未开始
 *   任一 approve_result = 2       → 已驳回   （驳回落在这一节点）
 *   任一 task_status = 0          → 待审批   （还有人没处理）
 *   任一 approve_result = 1       → 已通过
 *   其余（全部已跳过）             → 已跳过
 * </pre>
 *
 * <p>为什么要单独一个词表：任务级状态（{@code DictLabels#approvalTaskStatus}：待办/已办/已跳过）
 * 与节点级进度**不是一回事** —— 或签节点里「1 人已办 + 2 人被跳过」在任务级是三种取值，
 * 在节点级只有一句话「已通过」。两者混用，前端就会把「还有人待办」的节点画成已完成。
 */
@Getter
public enum ApprovalNodeProgress {

    /** 流程还没走到这个节点 */
    NOT_STARTED(0, "未开始"),

    /** 已到达，还有人没处理（含会签只过了一部分） */
    PENDING(1, "待审批"),

    /** 该节点已全部处理完并通过 */
    APPROVED(2, "已通过"),

    /** 该节点有人驳回（整单流程到此结束） */
    REJECTED(3, "已驳回"),

    /** 或签节点由他人处理，本节点的剩余任务全部被跳过，且无一人通过 */
    SKIPPED(4, "已跳过");

    private final Integer code;
    private final String label;

    ApprovalNodeProgress(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 码值 → 枚举，未知码值返回 {@code null}（读路径不抛异常） */
    public static ApprovalNodeProgress ofCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(p -> p.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /** 码值 → 中文标签，未知码值返回 {@code null} */
    public static String labelOf(Integer code) {
        ApprovalNodeProgress progress = ofCode(code);
        return progress == null ? null : progress.label;
    }
}
