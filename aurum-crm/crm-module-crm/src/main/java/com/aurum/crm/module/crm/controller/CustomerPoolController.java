package com.aurum.crm.module.crm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.CustomerPageReqVO;
import com.aurum.crm.module.crm.model.dto.CustomerPoolAssignReqVO;
import com.aurum.crm.module.crm.model.vo.CustomerRespVO;
import com.aurum.crm.module.crm.service.CustomerPoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 公海池 Controller，对应原型「公海」页。
 * 路由见《接口文档》4.4 公海模块。
 */
@Tag(name = "客户公海池")
@RestController
@RequestMapping("/crm/customer-pool")
@Validated
public class CustomerPoolController {

    @Resource
    private CustomerPoolService customerPoolService;

    @SaCheckPermission("crm:customerPool:list")
    @Operation(summary = "公海客户分页列表")
    @GetMapping
    public CommonResult<PageResult<CustomerRespVO>> page(CustomerPageReqVO reqVO) {
        return CommonResult.success(customerPoolService.poolPage(reqVO));
    }

    @SaCheckPermission("crm:customerPool:claim")
    @Operation(summary = "领取公海客户")
    @PostMapping("/{id}/claim")
    public CommonResult<Boolean> claim(@PathVariable("id") Long id) {
        customerPoolService.claim(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:customerPool:assign")
    @Operation(summary = "分配公海客户")
    @PostMapping("/{id}/assign")
    public CommonResult<Boolean> assign(@PathVariable("id") Long id,
                                        @Valid @RequestBody CustomerPoolAssignReqVO reqVO) {
        customerPoolService.assign(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:customerPool:delete")
    @Operation(summary = "删除公海客户")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        customerPoolService.delete(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:customerPool:list")
    @Operation(summary = "导出公海客户（Excel → OSS，返回下载 URL）")
    @GetMapping("/export")
    public CommonResult<String> export(CustomerPageReqVO reqVO) {
        return CommonResult.success(customerPoolService.export(reqVO));
    }

    @SaCheckPermission("crm:customerPool:list")
    @Operation(summary = "导出公海客户（XML/SpreadsheetML → OSS，返回下载 URL）")
    @GetMapping("/export-xml")
    public CommonResult<String> exportXml(CustomerPageReqVO reqVO) {
        return CommonResult.success(customerPoolService.exportXml(reqVO));
    }
}
