package com.aurum.crm.module.crm.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 审批进度里的**一个节点**（{@code sys_approval_node} 的模板行 + 它在本次审批中的进展）。
 *
 * <p>节点名 / 审批方式 / 审批人类型都取**模板现状**；已落库的任务保留自己的
 * {@code node_name} 快照（见 {@code ContractServiceImpl#createTask}），所以模板改过之后，
 * 下面的 {@code tasks[].nodeName}（如果有）与本节点的 {@code nodeName} 可能不一致 —— 这是有意的：
 * 前者是「当时是什么流程」，后者是「现在流程长什么样」。
 *
 * <p>{@code progress} 是把本节点全部任务归并后的一句话（{@code ApprovalNodeProgress}），
 * 与任务级状态不是一回事：或签节点「1 人已办 + 2 人已跳过」在任务级是两种取值，
 * 在节点级只有一句「已通过」。
 */
@Data
public class ContractApprovalNodeVO {

    private Long nodeId;

    /** 节点顺序，从 1 开始 */
    private Integer nodeOrder;

    private String nodeName;

    /** 审批方式中文标签：或签 / 会签 / 依次 */
    private String approveType;

    /** 审批人类型中文标签：指定用户 / 指定角色 / 部门主管 / 发起人直属上级 / 发起人本人 */
    private String approverType;

    /** 节点进度中文标签：未开始 / 待审批 / 已通过 / 已驳回 / 已跳过 */
    private String progress;

    /** 本节点**还在等谁**（姓名列表，即 {@code taskStatus = 待办} 的那些人） */
    private List<String> pendingApproverNames;

    /**
     * 本节点的**预计审批人**（姓名列表）—— 只在 {@code progress = 未开始} 时有值。
     *
     * <p>它由审批节点的配置 + 发起人解析而来（组织架构 / 角色成员会变，配置也会被改），
     * 所以是**预计值**，不是承诺；措辞上不要写成「即将审批人」。当前节点用
     * {@link #pendingApproverNames}（真实的待办人），两者不要混用。
     *
     * <p>空数组有两种含义：① 该节点不是「未开始」；② 确实解析不出人
     * （角色下没成员、发起人没有直属上级等）—— 后者意味着真走到这一步会失败。
     */
    private List<String> predictedApproverNames;

    /** 本节点已落库的全部任务（含已办 / 已跳过），按生成时间升序 */
    private List<ContractApprovalTaskVO> tasks;
}
