package com.aurum.crm.module.crm.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批进度里的**一个审批人的任务**（{@code sys_approval_task} 的一行）。
 *
 * <p>三个码值字段都在后端转成中文标签返回（与 {@code ContractRespVO.auditStatus} 同一套做法），
 * 前端直接渲染即可，不要再自己映射：
 * <ul>
 *     <li>{@code taskStatus} —— 待办 / 已办 / 已跳过（{@code DictLabels#approvalTaskStatus}）；</li>
 *     <li>{@code approveResult} —— 通过 / 驳回 / 撤销，**未处理时为 {@code null}**
 *         （{@code DictLabels#approveResult}）；</li>
 *     <li>{@code approverName} —— 跨表补的姓名，用户被删掉时可能为 {@code null}。</li>
 * </ul>
 *
 * <p>为什么「已跳过」要单独有一行：或签节点里，一个人通过后其余人的待办会被
 * {@code skipOtherTodoTasks} 置成「已跳过」。不展示它，用户就只看得到「谁审了」，
 * 看不到「本来还该谁审、为什么没审」—— 这正是本次要解决的问题。
 */
@Data
public class ContractApprovalTaskVO {

    private Long taskId;

    private Long approverId;

    /** 审批人姓名（跨表补值，查不到时为 null） */
    private String approverName;

    /** 任务状态中文标签：待办 / 已办 / 已跳过 */
    private String taskStatus;

    /** 审批结果中文标签：通过 / 驳回 / 撤销；未处理时为 null */
    private String approveResult;

    /** 审批意见 */
    private String comment;

    /** 处理时间，未处理时为 null */
    private LocalDateTime handleTime;

    /** 任务生成时间（该节点到达时间） */
    private LocalDateTime createTime;
}
