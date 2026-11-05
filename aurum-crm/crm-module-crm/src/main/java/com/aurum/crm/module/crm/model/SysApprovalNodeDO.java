package com.aurum.crm.module.crm.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_approval_node")
public class SysApprovalNodeDO {
    @TableId private Long nodeId;
    private Long flowId;
    private Integer nodeOrder;
    private String nodeName;
    private Integer approveType;
    private Integer approverType;
    private String approverValue;
    private Integer allowReject;
    private String remark;
    private LocalDateTime createTime;
    private Integer delFlag;
}
