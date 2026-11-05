package com.aurum.crm.module.bpm.model.dto;

import lombok.Data;

/** 审批流模板保存请求（新建/修改共用）。 */
@Data
public class FlowSaveReqVO {

    /** 流程编码（如 CONTRACT_AUDIT），唯一 */
    private String flowCode;

    /** 流程名称 */
    private String flowName;

    /** 业务类型（contract/invoice/payment） */
    private String bizType;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
