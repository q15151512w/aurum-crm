package com.aurum.crm.module.crm.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_approval_task")
public class SysApprovalTaskDO {
    @TableId private Long taskId;
    private Long instanceId;
    private Long nodeId;
    private Integer nodeOrder;
    private String nodeName;
    private Integer approveType;
    private Long approverId;
    private Integer taskStatus;
    private Integer approveResult;
    private String comment;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
}
