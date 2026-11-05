package com.aurum.crm.module.finance.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发票响应。
 */
@Data
@Schema(description = "发票响应")
public class InvoiceRespVO {

    @Schema(description = "发票ID")
    private Long id;

    @Schema(description = "发票申请编号")
    private String applyNo;

    @Schema(description = "实际发票号")
    private String invoiceNo;
    private Long paymentId;

    @Schema(description = "关联合同ID")
    private Long contractId;

    @Schema(description = "关联客户ID")
    private Long customerId;

    /**
     * 客户名称（补值）。
     *
     * <p>列表的「客户名称」列与编辑表单的客户回显都靠它。发票只存 {@code customerId}，
     * 不冗余客户名 —— 那是双写，客户改名后两张表必然不一致。
     */
    @Schema(description = "客户名称（补值）")
    private String customerName;

    @Schema(description = "归属人ID")
    private Long ownerUserId;

    /** 负责人姓名（补值，取 {@code sys_user.real_name}） */
    @Schema(description = "负责人姓名（补值）")
    private String ownerName;

    @Schema(description = "归属部门ID")
    private Long deptId;

    @Schema(description = "发票类型（1增值税专票 2增值税普票 3电子专票 4电子普票）")
    private Integer invoiceType;

    // ---- 客户开票信息快照（选中客户时带出，可改）----

    @Schema(description = "开票名称(抬头)")
    private String invoiceTitle;

    @Schema(description = "纳税人识别号")
    private String taxNo;

    @Schema(description = "开户行")
    private String bankName;

    @Schema(description = "银行账号")
    private String bankAccount;

    @Schema(description = "开票地址")
    private String invoiceAddr;

    @Schema(description = "开票电话")
    private String invoicePhone;

    @Schema(description = "不含税金额")
    private BigDecimal amount;

    /** 编辑表单要靠它回显税率 —— 少了这个字段，改一次发票就得让用户重填税率 */
    @Schema(description = "税率(%)")
    private BigDecimal taxRate;

    @Schema(description = "税额")
    private BigDecimal taxAmount;

    @Schema(description = "价税合计")
    private BigDecimal totalAmount;

    @Schema(description = "申请日期")
    private LocalDate applyDate;

    @Schema(description = "开票日期")
    private LocalDate issueDate;

    @Schema(description = "审核状态（0待提审 1审批中 2已通过 3已驳回）")
    private Integer auditStatus;

    @Schema(description = "单据状态（0待开票 1已开票 2已作废）")
    private Integer status;

    @Schema(description = "备注")
    private String remark;
    private java.util.List<Long> attachmentIds;

    @Schema(description = "回款明细（一票多回款；唯一事实来源）")
    private java.util.List<InvoicePaymentRespVO> allocations;

    @Schema(description = "当前登录人是否关注（crm_star，biz_type = 7）")
    private Boolean starred;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
