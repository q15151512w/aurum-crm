package com.aurum.crm.module.bpm.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批任务展示 VO，对齐接口文档八节「待办列表响应」字段。
 *
 * <p>{@code nodeName / bizType / bizId / bizNo / title / applyUserName} 都是从实例与
 * 节点快照补值出来的，任务表本身只存 {@code approverId} 与状态。
 */
@Data
public class ApprovalTaskRespVO {

    /** 任务ID */
    private Long taskId;

    /** 实例ID */
    private Long instanceId;

    /** 节点名（快照） */
    private String nodeName;

    /** 业务类型 */
    private String bizType;

    /** 业务单据ID */
    private Long bizId;

    /** 单据编号快照 */
    private String bizNo;

    /** 审批标题 */
    private String title;

    /** 发起人姓名 */
    private String applyUserName;

    /** 任务状态：0 待办 / 1 已办 / 2 已跳过 */
    private Integer taskStatus;

    /** 审批结果：1 通过 / 2 驳回 */
    private Integer approveResult;

    /** 审批意见 */
    private String comment;

    /** 处理时间 */
    private LocalDateTime handleTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
