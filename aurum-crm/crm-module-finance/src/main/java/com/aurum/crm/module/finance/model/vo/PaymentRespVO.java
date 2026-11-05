package com.aurum.crm.module.finance.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 回款响应。
 */
@Data
@Schema(description = "回款响应")
public class PaymentRespVO {

    @Schema(description = "回款ID")
    private Long id;

    @Schema(description = "回款编号")
    private String paymentNo;

    @Schema(description = "关联合同ID")
    private Long contractId;

    @Schema(description = "合同名称")
    private String contractName;

    @Schema(description = "关联客户ID")
    private Long customerId;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "归属人ID")
    private Long ownerUserId;

    @Schema(description = "归属部门ID")
    private Long deptId;

    @Schema(description = "回款金额")
    private BigDecimal amount;

    @Schema(description = "合同剩余未回款金额")
    private BigDecimal unpaidAmount;

    @Schema(description = "回款日期")
    private LocalDate paymentDate;

    @Schema(description = "回款方式（1银行转账 2现金 3支票 4在线支付 5其他）")
    private Integer paymentMethod;

    @Schema(description = "收款账户名")
    private String accountName;

    @Schema(description = "收款账号")
    private String accountNo;

    @Schema(description = "银行流水号")
    private String serialNo;

    @Schema(description = "审核状态（0待提审 1审批中 2已通过 3已驳回）")
    private Integer auditStatus;

    @Schema(description = "单据状态（0待确认 1已确认 2已作废）")
    private Integer status;

    @Schema(description = "当前登录人是否已关注该回款")
    private Boolean starred;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
