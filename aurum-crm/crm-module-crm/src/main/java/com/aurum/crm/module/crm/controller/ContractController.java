package com.aurum.crm.module.crm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.ContractAuditReqVO;
import com.aurum.crm.module.crm.model.dto.ContractBatchAuditReqVO;
import com.aurum.crm.module.crm.model.dto.ContractPageReqVO;
import com.aurum.crm.module.crm.model.dto.ContractSaveReqVO;
import com.aurum.crm.module.crm.model.vo.ContractApprovalRespVO;
import com.aurum.crm.module.crm.model.vo.ContractRespVO;
import com.aurum.crm.module.crm.service.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "合同管理")
@RestController
@RequestMapping("/crm/contract")
@Validated
public class ContractController {

    @Resource private ContractService contractService;

    @SaCheckPermission("crm:contract:list")
    @Operation(summary = "合同分页列表")
    @GetMapping
    public CommonResult<PageResult<ContractRespVO>> page(ContractPageReqVO reqVO) {
        return CommonResult.success(contractService.pageQuery(reqVO));
    }

    @SaCheckPermission("crm:contract:list")
    @Operation(summary = "导出合同（生成 Excel 上传 OSS，返回下载 URL）")
    @GetMapping("/export")
    public CommonResult<String> export(ContractPageReqVO reqVO) {
        return CommonResult.success(contractService.export(reqVO));
    }

    @SaCheckPermission("crm:contract:list")
    @Operation(summary = "导出合同（XML/SpreadsheetML → OSS，返回下载 URL）")
    @GetMapping("/export-xml")
    public CommonResult<String> exportXml(ContractPageReqVO reqVO) {
        return CommonResult.success(contractService.exportXml(reqVO));
    }

    @SaCheckPermission("crm:contract:auditList")
    @Operation(summary = "合同待审核列表")
    @GetMapping("/audit-list")
    public CommonResult<PageResult<ContractRespVO>> auditList(ContractPageReqVO reqVO) {
        return CommonResult.success(contractService.auditPage(reqVO));
    }

    @SaCheckPermission("crm:contract:list")
    @Operation(summary = "合同详情")
    @GetMapping("/{id}")
    public CommonResult<ContractRespVO> get(@PathVariable("id") Long id) {
        return CommonResult.success(contractService.get(id));
    }

    /**
     * 审批进度：走到哪一步、每步谁审的、现在还在等谁。
     *
     * <p>合同审批期间 {@code auditStatus} 一直是「审核中」，只看合同的列表/详情会以为审批没生效 ——
     * 这个端点把审批实例与任务翻译成可读的流程。
     *
     * <p>权限与详情一致（能看合同就能看它的流程），所以**不新增权限点**，不用改 {@code sys_menu}。
     */
    @SaCheckPermission("crm:contract:list")
    @Operation(summary = "合同审批进度")
    @GetMapping("/{id}/approval")
    public CommonResult<ContractApprovalRespVO> approval(@PathVariable("id") Long id) {
        return CommonResult.success(contractService.approval(id));
    }

    @SaCheckPermission("crm:contract:create")
    @Operation(summary = "新增合同")
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody ContractSaveReqVO reqVO) {
        return CommonResult.success(contractService.create(reqVO));
    }

    @SaCheckPermission("crm:contract:update")
    @Operation(summary = "修改合同")
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable("id") Long id,
                                        @Valid @RequestBody ContractSaveReqVO reqVO) {
        contractService.update(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:contract:delete")
    @Operation(summary = "删除合同")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        contractService.delete(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:contract:submit")
    @Operation(summary = "提交合同审核")
    @PostMapping("/{id}/submit")
    public CommonResult<Boolean> submit(@PathVariable("id") Long id) {
        contractService.submit(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:contract:audit")
    @Operation(summary = "审核合同")
    @PostMapping("/{id}/audit")
    public CommonResult<Boolean> audit(@PathVariable("id") Long id,
                                       @Valid @RequestBody ContractAuditReqVO reqVO) {
        contractService.audit(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:contract:batchAudit")
    @Operation(summary = "批量审核合同")
    @PostMapping("/batch-audit")
    public CommonResult<Boolean> batchAudit(@Valid @RequestBody ContractBatchAuditReqVO reqVO) {
        contractService.batchAudit(reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:contract:void")
    @Operation(summary = "作废合同")
    @PostMapping("/{id}/void")
    public CommonResult<Boolean> voidContract(@PathVariable("id") Long id) {
        contractService.voidContract(id);
        return CommonResult.success(true);
    }

    /**
     * 关注 / 取消关注，路由与线索/客户/商机/报价单对齐（POST + DELETE，非 toggle，保证幂等）。
     */
    @SaCheckPermission("crm:contract:list")
    @Operation(summary = "关注合同")
    @PostMapping("/{id}/star")
    public CommonResult<Boolean> star(@PathVariable("id") Long id) {
        contractService.star(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:contract:list")
    @Operation(summary = "取消关注合同")
    @DeleteMapping("/{id}/star")
    public CommonResult<Boolean> unstar(@PathVariable("id") Long id) {
        contractService.unstar(id);
        return CommonResult.success(true);
    }
}
