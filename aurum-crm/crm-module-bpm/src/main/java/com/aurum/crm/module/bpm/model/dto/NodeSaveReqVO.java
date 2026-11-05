package com.aurum.crm.module.bpm.model.dto;

import lombok.Data;

/** 审批流节点保存请求。 */
@Data
public class NodeSaveReqVO {

    /** 节点顺序（从 1 开始） */
    private Integer nodeOrder;

    /** 节点名称 */
    private String nodeName;

    /** 审批方式：1 或签 / 2 会签 / 3 依次 */
    private Integer approveType;

    /** 审批人类型：1 指定用户 / 2 指定角色 / 3 部门主管 / 4 发起人直属上级 / 5 发起人本人 */
    private Integer approverType;

    /** 审批人取值（用户/角色 ID 数组 JSON，如 [1,2,3]；类型 3/4/5 时留空） */
    private String approverValue;

    /** 是否允许驳回：0 否 / 1 是 */
    private Integer allowReject;

    /** 备注 */
    private String remark;
}
