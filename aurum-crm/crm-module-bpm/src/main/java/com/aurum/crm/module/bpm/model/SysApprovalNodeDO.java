package com.aurum.crm.module.bpm.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 审批流节点，对应 {@code sys_approval_node}。 */
@Data
@TableName("sys_approval_node")
public class SysApprovalNodeDO {
    @TableId
    private Long nodeId;
    private Long flowId;
    private Integer nodeOrder;
    private String nodeName;
    /** {@code approve_type}：1 或签 / 2 会签 / 3 依次审批 */
    private Integer approveType;
    /** {@code approver_type}：1 指定人员 / 2 角色 / 3 部门主管 / 4 直属上级 / 5 发起人自己 */
    private Integer approverType;
    private String approverValue;
    private Integer allowReject;
    private String remark;
    private LocalDateTime createTime;
    private Integer delFlag;
}
