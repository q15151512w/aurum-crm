package com.aurum.crm.module.bpm.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 审批实例展示 VO（「我发起的审批」与「按单据查进度」共用）。 */
@Data
public class ApprovalInstanceRespVO {

    /** 实例ID */
    private Long instanceId;

    /** 流程ID */
    private Long flowId;

    /** 流程名称 */
    private String flowName;

    /** 业务类型 */
    private String bizType;

    /** 业务单据ID */
    private Long bizId;

    /** 单据编号快照 */
    private String bizNo;

    /** 审批标题 */
    private String title;

    /** 发起人ID */
    private Long applyUserId;

    /** 发起人姓名 */
    private String applyUserName;

    /** 当前节点顺序 */
    private Integer currentNodeOrder;

    /** 状态：0 审批中 / 1 已通过 / 2 已驳回 / 3 已撤销 */
    private Integer status;

    /** 发起时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;
}
