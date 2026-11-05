package com.aurum.crm.module.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.finance.model.dto.InvoicePageReqVO;
import com.aurum.crm.module.finance.model.dto.InvoiceSaveReqVO;
import com.aurum.crm.module.finance.model.dto.InvoiceIssueReqVO;
import com.aurum.crm.module.finance.model.dto.InvoiceRejectReqVO;
import com.aurum.crm.module.finance.model.vo.InvoiceCustomerFormRespVO;
import com.aurum.crm.module.finance.model.vo.InvoiceRespVO;
import com.aurum.crm.module.finance.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 发票管理 Controller，对应接口文档 7.2 发票。
 *
 * <p>登记（CRUD / 提交）/ 财务处置（开票 / 拒绝 / 作废）/ 查询导出均已实现。
 * 接口文档 7.3 的独立「核销」端点未实现 —— 发票与回款的关联在保存发票时
 * 由 {@code fin_invoice_payment} 一并落库，该端点是否还需要保留待定。
 *
 * <p>鉴权：所有端点都带 {@code @SaCheckPermission}，权限码取自 {@code sql/crm.sql}
 * 的 {@code sys_menu}。财务处置动作（开票 / 拒绝 / 作废）共用 {@code fin:invoice:issue}
 * —— 它们是同一个待办页上的同一类动作。
 */
@Tag(name = "发票管理")
@RestController
@RequestMapping("/finance/invoice")
@Validated
public class InvoiceController {

    @Resource
    private InvoiceService invoiceService;

    @SaCheckPermission("fin:invoice:list")
    @Operation(summary = "发票分页列表")
    @GetMapping
    public CommonResult<PageResult<InvoiceRespVO>> page(InvoicePageReqVO reqVO) {
        return CommonResult.success(invoiceService.pageQuery(reqVO));
    }

    @SaCheckPermission("fin:invoice:issueList")
    @GetMapping("/audit-list")
    public CommonResult<PageResult<InvoiceRespVO>> auditList(InvoicePageReqVO req) { return CommonResult.success(invoiceService.auditPage(req)); }
    @SaCheckPermission("fin:invoice:list")
    @GetMapping("/{id}")
    public CommonResult<InvoiceRespVO> get(@PathVariable Long id) { return CommonResult.success(invoiceService.get(id)); }

    /**
     * 「新增/编辑发票登记」表单选好客户后的联动数据。
     *
     * <p>作用见
     * <a href="prototype-shots/发票登记.png">原型</a> 的黄色标注：
     * 「选择客户后，1、显示该客户下所有未全部开票的回款信息。2、自动带出客户的
     * 发票抬头，支持调整」。
     *
     * <p>字面量路径段 {@code customer-form} 不会被 {@code @GetMapping("/{id}")} 吞掉
     * ——Spring 的路径匹配里字面量优先于变量。
     *
     * <p>权限用 OR：新建时校验 create、编辑时校验 update，前端一个表单两处调用。
     * 两个权限码在 {@code sys_menu} 里已存在，无需新增菜单行。
     */
    @SaCheckPermission(value = {"fin:invoice:create", "fin:invoice:update"}, mode = SaMode.OR)
    @Operation(summary = "发票表单-选客户联动（默认开票信息 + 可开票回款）")
    @GetMapping("/customer-form")
    public CommonResult<InvoiceCustomerFormRespVO> customerForm(@RequestParam Long customerId,
                                                               @RequestParam(required = false) Long invoiceId) {
        return CommonResult.success(invoiceService.customerForm(customerId, invoiceId));
    }
    @SaCheckPermission("fin:invoice:create")
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody InvoiceSaveReqVO req) { return CommonResult.success(invoiceService.create(req)); }
    @SaCheckPermission("fin:invoice:update")
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable Long id, @Valid @RequestBody InvoiceSaveReqVO req) { invoiceService.update(id, req); return CommonResult.success(true); }
    @SaCheckPermission("fin:invoice:delete")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable Long id) { invoiceService.delete(id); return CommonResult.success(true); }
    /**
     * 关注 / 取消关注。
     *
     * <p>权限码用 {@code fin:invoice:list}（不是 create/update）：关注是「读」的
     * 变体 —— 只看不写的人也应该能标记，用写权限码会把财务角色挡在外面。
     * 同样，{@code required(id)} 已经保证了单据在调用人的数据范围内。
     */
    @SaCheckPermission("fin:invoice:list")
    @PostMapping("/{id}/star")
    public CommonResult<Boolean> star(@PathVariable Long id) { invoiceService.star(id); return CommonResult.success(true); }

    @SaCheckPermission("fin:invoice:list")
    @DeleteMapping("/{id}/star")
    public CommonResult<Boolean> unstar(@PathVariable Long id) { invoiceService.unstar(id); return CommonResult.success(true); }

    @SaCheckPermission("fin:invoice:submit")
    @PostMapping("/{id}/submit")
    public CommonResult<Boolean> submit(@PathVariable Long id) { invoiceService.submit(id); return CommonResult.success(true); }
    @SaCheckPermission("fin:invoice:issue")
    @PostMapping("/{id}/issue")
    public CommonResult<Boolean> issue(@PathVariable Long id, @Valid @RequestBody InvoiceIssueReqVO req) { invoiceService.issue(id, req); return CommonResult.success(true); }

    /**
     * 财务拒绝开票。
     *
     * <p>权限码用 {@code fin:invoice:issue} 而非新增一个：拒绝和开票是<b>同一个财务动作</b>
     * 的两面，都发生在「财务开票」这个待办页上。复用现有码可以不加菜单行、
     * 不动角色授权种子，也不会出现「能开票却不能拒绝」这种没人想要的中间态。
     */
    @SaCheckPermission("fin:invoice:issue")
    @PostMapping("/{id}/reject")
    public CommonResult<Boolean> reject(@PathVariable Long id, @Valid @RequestBody InvoiceRejectReqVO req) { invoiceService.reject(id, req); return CommonResult.success(true); }

    @SaCheckPermission("fin:invoice:void")
    @PostMapping("/{id}/void")
    public CommonResult<Boolean> voidInvoice(@PathVariable Long id) { invoiceService.voidInvoice(id); return CommonResult.success(true); }

    @SaCheckPermission("fin:invoice:export")
    @Operation(summary = "导出发票（Excel → OSS，返回下载 URL）")
    @GetMapping("/export")
    public CommonResult<String> export(InvoicePageReqVO reqVO) {
        return CommonResult.success(invoiceService.export(reqVO));
    }

    @SaCheckPermission("fin:invoice:export")
    @Operation(summary = "导出发票（XML/SpreadsheetML → OSS，返回下载 URL）")
    @GetMapping("/export-xml")
    public CommonResult<String> exportXml(InvoicePageReqVO reqVO) {
        return CommonResult.success(invoiceService.exportXml(reqVO));
    }
}
