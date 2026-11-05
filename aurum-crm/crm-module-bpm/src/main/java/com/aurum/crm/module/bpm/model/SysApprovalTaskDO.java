package com.aurum.crm.module.bpm.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 审批任务（待办），对应 {@code sys_approval_task}。 */
@Data
@TableName("sys_approval_task")
public class SysApprovalTaskDO {
    @TableId
    private Long taskId;
    private Long instanceId;
    private Long nodeId;
    private Integer nodeOrder;
    private String nodeName;
    private Integer approveType;
    private Long approverId;
    /** 0 待办 / 1 已处理 / 2 已跳过 */
    private Integer taskStatus;
    /** 1 通过 / 2 驳回 / 3 跳过 */
    private Integer approveResult;
    private String comment;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
}
