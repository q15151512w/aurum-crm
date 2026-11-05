package com.aurum.crm.module.finance.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 回款列表 / 导出 分页查询请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "回款分页查询")
public class PaymentPageReqVO extends PageParam {

    @Schema(description = "回款编号（模糊）")
    private String paymentNo;

    @Schema(description = "关联客户ID")
    private Long customerId;

    @Schema(description = "关联合同ID")
    private Long contractId;

    @Schema(description = "归属人ID")
    private Long ownerUserId;

    @Schema(description = "审核状态（0待提审 1审批中 2已通过 3已驳回）")
    private Integer auditStatus;

    @Schema(description = "单据状态（0待确认 1已确认 2已作废）")
    private Integer status;

    @Schema(description = "关键字（回款编号）")
    private String keyword;

    @Schema(description = "列表口径：all 全部 / mine 我负责的 / subordinate 下属负责的 / starred 我关注的",
            example = "all", allowableValues = {"all", "mine", "subordinate", "starred"})
    private String scope;
}
