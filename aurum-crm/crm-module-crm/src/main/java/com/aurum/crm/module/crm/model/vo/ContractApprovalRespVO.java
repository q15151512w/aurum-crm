package com.aurum.crm.module.crm.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 合同审批进度（{@code GET /crm/contract/{id}/approval}）。
 *
 * <p><b>它解决什么问题</b>：合同审批期间 {@code crm_contract.audit_status} 一直是「审核中」，
 * 而 {@code CONTRACT_AUDIT} 是**两级**流程（部门主管 → 财务）。只看合同那一列，既看不出
 * 走到第几步、也看不出现在卡在谁那里 —— 主管审完仍是「审核中」，看起来像没生效。
 * 本 VO 把 {@code sys_approval_instance / node / task} 三张表翻译成「一条流程 + 每步结果」。
 *
 * <p><b>两个状态字段的分工</b>（别混用）：
 * <ul>
 *     <li>{@code auditStatus} —— 合同**审核态**（未提交 / 审核中 / 审核通过 / 审核拒绝），
 *         取自 {@code crm_contract.audit_status}，与列表、详情页口径完全一致
 *         （{@code ContractAuditStatus}）；</li>
 *     <li>{@code instanceStatus} —— **审批实例**状态（审批中 / 已通过 / 已驳回 / 已撤销），
 *         取自 {@code sys_approval_instance.status}；没有实例时为 {@code null}。</li>
 * </ul>
 * 正常情况两者同向变化（都「审核中」；都「通过」）；不一致时 {@code abnormalHint} 会给出说明。
 *
 * <p><b>没有实例也会返回东西</b>：草稿（未提交）或历史脏数据（演示库的 {@code HT20260003}
 * 就是「标记审核中但没有实例」）时，{@code nodes} 用**当前启用的模板**渲染，
 * 全部是「未开始」，让人至少知道这条合同要走几步；脏数据另有 {@code abnormalHint} 说明。
 */
@Data
public class ContractApprovalRespVO {

    private Long contractId;

    private String contractNo;

    private String contractName;

    /** 合同审核态中文标签：未提交 / 审核中 / 审核通过 / 审核拒绝 */
    private String auditStatus;

    /** 审批流名称（如「合同审核流」）；连模板都取不到时为 null */
    private String flowName;

    /** 审批实例ID；未提交或实例缺失时为 null */
    private Long instanceId;

    /** 审批实例状态中文标签：审批中 / 已通过 / 已驳回 / 已撤销；无实例时为 null */
    private String instanceStatus;

    /** 发起人姓名（跨表补值） */
    private String applyUserName;

    /** 发起时间 */
    private LocalDateTime applyTime;

    /** 流程结束时间，未结束为 null */
    private LocalDateTime endTime;

    /** 当前节点序号；非「审批中」时为 null */
    private Integer currentNodeOrder;

    /** 当前节点名称（如「财务审批」）；非「审批中」时为 null */
    private String currentNodeName;

    /**
     * 当前节点**还在等谁**（姓名列表）。
     *
     * <p>或签节点会有多个人同时待办（任一通过即可），所以是列表而不是单个名字。
     * 非「审批中」时为空列表。
     */
    private List<String> pendingApproverNames;

    /**
     * **下一步到谁**：当前节点之后第一个节点的序号 / 名称 / 预计审批人。
     *
     * <p>已经是最后一个节点、或流程尚未/已经结束时，这三个字段为空
     * （`non_null` 下不出现在 JSON 里）。预计审批人同样只是**预计**
     * （见 {@link ContractApprovalNodeVO#getPredictedApproverNames()}）。
     */
    private Integer nextNodeOrder;

    private String nextNodeName;

    private List<String> nextApproverNames;

    /**
     * 数据异常提示，正常为 {@code null}。三种情形见 {@code ContractServiceImpl#abnormalHint}：
     * 审核中却没有实例（演示数据遗留，任何审批动作都会报 4003）、实例已结束但合同仍审核中、
     * 实例进行中但合同审核态不是审核中。
     */
    private String abnormalHint;

    /** 节点进度，按 {@code node_order} 升序。模板未配置节点时为空列表 */
    private List<ContractApprovalNodeVO> nodes;
}
