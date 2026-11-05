package com.aurum.crm.module.finance.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 「新增/编辑发票登记」表单选好客户后一次性返回的联动数据。
 *
 * <p>合成一个接口而不是让前端连打两个，是因为这两份数据必须在<b>同一个</b>
 * 权限与事务快照下读出：如果分两次请求，中间恰有同事提交了一张发票，
 * 前端就会拿到「已分配额为旧值」的回款列表，用户按剩余额度填完再保存必然被校验拒绝。
 */
@Data
@Schema(description = "发票表单-客户联动数据")
public class InvoiceCustomerFormRespVO {

    /**
     * 客户默认开票信息；客户尚未维护时为 {@code null}。
     *
     * <p>为 null 是正常分支：前端把六个字段留空让用户手填。
     */
    @Schema(description = "客户默认开票信息，客户未维护时为 null")
    private CustomerInvoiceInfoVO invoiceInfo;

    /** 该客户当下仍可开票的回款（剩余可开金额 > 0） */
    @Schema(description = "可开票回款候选")
    private List<InvoiceablePaymentVO> payments;
}
