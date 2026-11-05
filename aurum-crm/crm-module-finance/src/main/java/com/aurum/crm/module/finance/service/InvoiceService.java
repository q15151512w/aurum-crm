package com.aurum.crm.module.finance.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.finance.model.dto.InvoicePageReqVO;
import com.aurum.crm.module.finance.model.dto.InvoiceSaveReqVO;
import com.aurum.crm.module.finance.model.dto.InvoiceIssueReqVO;
import com.aurum.crm.module.finance.model.dto.InvoiceRejectReqVO;
import com.aurum.crm.module.finance.model.vo.InvoiceCustomerFormRespVO;
import com.aurum.crm.module.finance.model.vo.InvoiceRespVO;

/**
 * 发票 Service：分页查询 + 导出（Excel → OSS → URL）。
 *
 * <p>发票采用「一票多回款」模型：一张发票可合并该客户的多笔已确认回款，
 * 每笔在发票内各有「本次开票金额」，金额由服务端逐行反算后汇总。
 */
public interface InvoiceService {

    /**
     * 发票分页列表（含数据权限过滤，响应带 {@code allocations} 明细）。
     */
    PageResult<InvoiceRespVO> pageQuery(InvoicePageReqVO reqVO);
    PageResult<InvoiceRespVO> auditPage(InvoicePageReqVO reqVO);
    InvoiceRespVO get(Long id);

    /**
     * 「新增/编辑发票登记」表单选好客户后的联动数据：默认开票信息 + 可开票回款候选。
     *
     * @param customerId 客户ID
     * @param invoiceId  编辑时为当前发票ID（排除自身占用，使本发票已占满的回款仍可选）；新增传 null
     */
    InvoiceCustomerFormRespVO customerForm(Long customerId, Long invoiceId);
    Long create(InvoiceSaveReqVO req);
    void update(Long id, InvoiceSaveReqVO req);
    void delete(Long id);
    void submit(Long id);
    void issue(Long id, InvoiceIssueReqVO req);

    /**
     * 财务拒绝开票：{@code auditStatus} 退回 3 已驳回，{@code status} 留在 0 待开票，
     * 申请人改完可再次 {@link #submit} 重新提审（{@code editable} 已放行 3，闭环成立）。
     *
     * <p>刻意<b>不删</b> {@code fin_invoice_payment} 明细：被占用的回款开票额度保持占用，
     * 申请人重新提交时不必重填明细。要彻底释放应走「删除发票」而非拒绝。
     */
    void reject(Long id, InvoiceRejectReqVO req);

    void voidInvoice(Long id);

    /**
     * 关注 / 取消关注发票（写入通用关注表 {@code crm_star}，biz_type = 7）。
     *
     * <p>两个都幂等：重复关注 / 重复取消不会报错，也不产生重复行。
     */
    void star(Long id);

    void unstar(Long id);

    /**
     * 导出发票（生成 Excel → 上传 OSS → 返回下载 URL），含当前过滤条件。
     */
    String export(InvoicePageReqVO reqVO);

    /**
     * 导出发票（生成 XML/SpreadsheetML → 上传 OSS → 返回下载 URL），含当前过滤条件。
     */
    String exportXml(InvoicePageReqVO reqVO);
}
