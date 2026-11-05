package com.aurum.crm.module.bpm.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 审批流模板，对应 {@code sys_approval_flow}。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_approval_flow")
public class SysApprovalFlowDO extends BaseDO {
    @TableId
    private Long flowId;
    private String flowCode;
    private String flowName;
    private String bizType;
    private Integer status;
    private String remark;
}
