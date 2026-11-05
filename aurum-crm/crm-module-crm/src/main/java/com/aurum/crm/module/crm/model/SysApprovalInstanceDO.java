package com.aurum.crm.module.crm.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_approval_instance")
public class SysApprovalInstanceDO extends BaseDO {
    @TableId private Long instanceId;
    private Long flowId;
    private String bizType;
    private Long bizId;
    private String bizNo;
    private String title;
    private Long applyUserId;
    private Long applyDeptId;
    private Integer currentNodeOrder;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
