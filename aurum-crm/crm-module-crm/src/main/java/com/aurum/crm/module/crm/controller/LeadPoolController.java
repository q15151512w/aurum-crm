package com.aurum.crm.module.crm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.LeadPageReqDTO;
import com.aurum.crm.module.crm.model.dto.LeadPoolAssignReqDTO;
import com.aurum.crm.module.crm.model.vo.LeadRespVO;
import com.aurum.crm.module.crm.service.LeadPoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 线索池 Controller，对应原型「线索池」页。
 * 路由见《接口文档》4.2 线索池模块（等同 {@code /crm/lead} 查 {@code owner IS NULL}）。
 */
@Tag(name = "线索池")
@RestController
@RequestMapping("/crm/lead-pool")
@Validated
public class LeadPoolController {

    @Resource
    private LeadPoolService leadPoolService;

    @SaCheckPermission("crm:leadPool:list")
    @Operation(summary = "线索池分页列表")
    @GetMapping
    public CommonResult<PageResult<LeadRespVO>> page(LeadPageReqDTO reqDTO) {
        return CommonResult.success(leadPoolService.poolPage(reqDTO));
    }

    @SaCheckPermission("crm:leadPool:claim")
    @Operation(summary = "领取线索池线索")
    @PostMapping("/{id}/claim")
    public CommonResult<Boolean> claim(@PathVariable("id") Long id) {
        leadPoolService.claim(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:leadPool:assign")
    @Operation(summary = "分配线索池线索")
    @PostMapping("/{id}/assign")
    public CommonResult<Boolean> assign(@PathVariable("id") Long id,
                                        @Valid @RequestBody LeadPoolAssignReqDTO reqDTO) {
        leadPoolService.assign(id, reqDTO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:leadPool:delete")
    @Operation(summary = "删除线索池线索")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        leadPoolService.delete(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:leadPool:list")
    @Operation(summary = "导出线索池线索（Excel → OSS，返回下载 URL）")
    @GetMapping("/export")
    public CommonResult<String> export(LeadPageReqDTO reqDTO) {
        return CommonResult.success(leadPoolService.export(reqDTO));
    }

    @SaCheckPermission("crm:leadPool:list")
    @Operation(summary = "导出线索池线索（XML/SpreadsheetML → OSS，返回下载 URL）")
    @GetMapping("/export-xml")
    public CommonResult<String> exportXml(LeadPageReqDTO reqDTO) {
        return CommonResult.success(leadPoolService.exportXml(reqDTO));
    }
}
