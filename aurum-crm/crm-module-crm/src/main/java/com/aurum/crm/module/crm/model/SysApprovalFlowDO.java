package com.aurum.crm.module.crm.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_approval_flow")
public class SysApprovalFlowDO extends BaseDO {
    @TableId private Long flowId;
    private String flowCode;
    private String flowName;
    private String bizType;
    private Integer status;
    private String remark;
}
