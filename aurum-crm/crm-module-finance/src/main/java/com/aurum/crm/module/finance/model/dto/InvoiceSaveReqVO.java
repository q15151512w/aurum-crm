package com.aurum.crm.module.finance.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 发票新增 / 编辑请求。
 *
 * <p>⚠️ <b>请求体里没有 {@code customerId}</b>：发票的客户由所选回款推导
 * （所有明细必须同客户，见 {@code INVOICE_PAYMENT_CUSTOMER_MISMATCH}）。
 * 让前端同时传客户和回款只会制造「两者不一致」的新失败模式。
 *
 * <p>同理没有金额字段：{@code amount/tax_amount/total_amount} 全部由服务端
 * 按明细与税率算，避免前端算错导致父子单据对不上。
 */
@Data
public class InvoiceSaveReqVO {

    @NotNull(message = "发票类型不能为空")
    private Integer invoiceType;

    @NotNull(message = "税率不能为空")
    @DecimalMin(value = "0", message = "税率不能小于 0")
    @DecimalMax(value = "100", message = "税率不能大于 100")
    private BigDecimal taxRate;

    @NotNull(message = "申请日期不能为空")
    private LocalDate applyDate;

    @Size(max = 500, message = "备注最多 500 字")
    private String remark;

    /** 回款明细，至少一笔。金额之和即发票价税合计 */
    @Valid
    @NotEmpty(message = "请至少选择一笔回款")
    private List<InvoicePaymentReqVO> items;

    // ---- 客户开票信息快照：选中客户时由前端带出，允许用户改动 ----

    @Size(max = 128) private String invoiceTitle;
    @Size(max = 64)  private String taxNo;
    @Size(max = 128) private String bankName;
    @Size(max = 64)  private String bankAccount;
    @Size(max = 255) private String invoiceAddr;
    @Size(max = 32)  private String invoicePhone;

    private List<Long> attachmentIds;
}
