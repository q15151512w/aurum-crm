package com.aurum.crm.module.bpm.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 审批实例，对应 {@code sys_approval_instance}。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_approval_instance")
public class SysApprovalInstanceDO extends BaseDO {
    @TableId
    private Long instanceId;
    private Long flowId;
    private String bizType;
    private Long bizId;
    private String bizNo;
    private String title;
    private Long applyUserId;
    private Long applyDeptId;
    private Integer currentNodeOrder;
    /** 0 进行中 / 1 已通过 / 2 已驳回 */
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
