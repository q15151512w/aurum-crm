package com.aurum.crm.module.crm.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "合同分页查询")
public class ContractPageReqVO extends PageParam {
    private String keyword;
    private String contractName;
    private Long customerId;
    private Long opportunityId;
    private Long quotationId;
    private Long ownerUserId;
    private Integer auditStatus;
    private Integer status;

    @Schema(description = "列表口径：all 全部 / mine 我负责的 / subordinate 下属负责的 / starred 我关注的",
            example = "all", allowableValues = {"all", "mine", "subordinate", "starred"})
    private String scope;
}
