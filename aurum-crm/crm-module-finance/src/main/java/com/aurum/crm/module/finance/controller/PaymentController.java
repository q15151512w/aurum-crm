package com.aurum.crm.module.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.finance.model.dto.PaymentPageReqVO;
import com.aurum.crm.module.finance.model.dto.PaymentSaveReqVO;
import com.aurum.crm.module.finance.model.dto.PaymentAuditReqVO;
import com.aurum.crm.module.finance.model.vo.PaymentRespVO;
import com.aurum.crm.module.finance.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 回款管理 Controller，对应接口文档 7.1 回款。
 *
 * <p>登记（CRUD）、提交审核、财务审核、关注、导出均已实现。接口文档 7.3 的独立
 * 「核销」端点未实现 —— 发票与回款的关联在保存发票时由 {@code fin_invoice_payment}
 * 一并落库，该端点是否还需要保留待定（与 {@code InvoiceController} 同一结论）。
 *
 * <p><b>鉴权</b>：{@code @SaCheckPermission} 已补齐，权限码取自 {@code sql/crm.sql} 的
 * {@code sys_menu}：列表用 {@code fin:payment:list}，两个导出用 {@code fin:payment:export}。
 * ⚠️ 这两个码在种子里<b>分别授予不同角色</b>：销售总监 / 销售专员只有 {@code :list}，
 * {@code :export} 只有财务和超管有 —— 也就是导出必须真的被拦下来。
 * 此前这两个端点没有任何校验（前端藏了按钮、后端却照常执行），补齐后才与授权模型一致。
 * 行级范围另由数据权限拦截器控制（{@code fin_payment} 在白名单里，且 {@code owner_user_id} 非空）。
 */
@Tag(name = "回款管理")
@RestController
@RequestMapping("/finance/payment")
@Validated
public class PaymentController {

    @Resource
    private PaymentService paymentService;

    @SaCheckPermission("fin:payment:list")
    @Operation(summary = "回款分页列表")
    @GetMapping
    public CommonResult<PageResult<PaymentRespVO>> page(PaymentPageReqVO reqVO) {
        return CommonResult.success(paymentService.pageQuery(reqVO));
    }

    @SaCheckPermission("fin:payment:auditList")
    @GetMapping("/audit-list")
    public CommonResult<PageResult<PaymentRespVO>> auditList(PaymentPageReqVO reqVO) {
        return CommonResult.success(paymentService.auditPage(reqVO));
    }

    @SaCheckPermission("fin:payment:list")
    @GetMapping("/{id}")
    public CommonResult<PaymentRespVO> get(@PathVariable Long id) {
        return CommonResult.success(paymentService.get(id));
    }

    @SaCheckPermission("fin:payment:create")
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody PaymentSaveReqVO reqVO) {
        return CommonResult.success(paymentService.create(reqVO));
    }

    @SaCheckPermission("fin:payment:update")
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable Long id, @Valid @RequestBody PaymentSaveReqVO reqVO) {
        paymentService.update(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("fin:payment:delete")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        paymentService.delete(id);
        return CommonResult.success(true);
    }

    /**
     * 关注 / 取消关注，路由与线索/客户/商机/报价单/合同对齐（POST + DELETE，非 toggle，保证幂等）。
     */
    @SaCheckPermission("fin:payment:list")
    @Operation(summary = "关注回款")
    @PostMapping("/{id}/star")
    public CommonResult<Boolean> star(@PathVariable Long id) {
        paymentService.star(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("fin:payment:list")
    @Operation(summary = "取消关注回款")
    @DeleteMapping("/{id}/star")
    public CommonResult<Boolean> unstar(@PathVariable Long id) {
        paymentService.unstar(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("fin:payment:submit")
    @PostMapping("/{id}/submit")
    public CommonResult<Boolean> submit(@PathVariable Long id) {
        paymentService.submit(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("fin:payment:audit")
    @PostMapping("/{id}/audit")
    public CommonResult<Boolean> audit(@PathVariable Long id, @Valid @RequestBody PaymentAuditReqVO reqVO) {
        paymentService.audit(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("fin:payment:export")
    @Operation(summary = "导出回款（Excel → OSS，返回下载 URL）")
    @GetMapping("/export")
    public CommonResult<String> export(PaymentPageReqVO reqVO) {
        return CommonResult.success(paymentService.export(reqVO));
    }

    @SaCheckPermission("fin:payment:export")
    @Operation(summary = "导出回款（XML/SpreadsheetML → OSS，返回下载 URL）")
    @GetMapping("/export-xml")
    public CommonResult<String> exportXml(PaymentPageReqVO reqVO) {
        return CommonResult.success(paymentService.exportXml(reqVO));
    }
}
