package com.aurum.crm.module.crm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.CustomerPageReqVO;
import com.aurum.crm.module.crm.model.dto.CustomerSaveReqVO;
import com.aurum.crm.module.crm.model.dto.CustomerSimplePageReqVO;
import com.aurum.crm.module.crm.model.dto.ToPoolReqVO;
import com.aurum.crm.module.crm.model.vo.CustomerRespVO;
import com.aurum.crm.module.crm.model.vo.CustomerSimpleRespVO;
import com.aurum.crm.module.crm.model.vo.SalesOwnerRespVO;
import com.aurum.crm.module.crm.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户管理 Controller，对应原型「客户」页。
 * 路由见《接口文档》4.3 客户模块。
 */
@Tag(name = "客户管理")
@RestController
@RequestMapping("/crm/customer")
@Validated
public class CustomerController {

    @Resource
    private CustomerService customerService;

    @SaCheckPermission("crm:customer:list")
    @Operation(summary = "客户分页列表")
    @GetMapping
    public CommonResult<PageResult<CustomerRespVO>> page(CustomerPageReqVO reqVO) {
        return CommonResult.success(customerService.pageQuery(reqVO));
    }

    @SaCheckPermission("crm:customer:list")
    @Operation(summary = "客户选择器")
    @GetMapping("/simple-list")
    public CommonResult<PageResult<CustomerSimpleRespVO>> simpleList(CustomerSimplePageReqVO reqVO) {
        return CommonResult.success(customerService.simpleList(reqVO));
    }

    @SaCheckPermission("crm:customer:list")   // 新增/修改/公海分配共用的负责人下拉
    @Operation(summary = "销售负责人下拉选项")
    @GetMapping("/owner-options")
    public CommonResult<List<SalesOwnerRespVO>> ownerOptions() {
        return CommonResult.success(customerService.ownerOptions());
    }

    @SaCheckPermission("crm:customer:list")   // 详情复用列表权限，种子菜单 menu_id=23 就是它
    @Operation(summary = "客户详情")
    @GetMapping("/{id}")
    public CommonResult<CustomerRespVO> get(@PathVariable("id") Long id) {
        return CommonResult.success(customerService.get(id));
    }

    @SaCheckPermission("crm:customer:create")
    @Operation(summary = "新增客户")
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody CustomerSaveReqVO reqVO) {
        return CommonResult.success(customerService.create(reqVO));
    }

    @SaCheckPermission("crm:customer:update")
    @Operation(summary = "修改客户")
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable("id") Long id,
                                        @Valid @RequestBody CustomerSaveReqVO reqVO) {
        customerService.update(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:customer:delete")
    @Operation(summary = "删除客户")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        customerService.delete(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:customer:toPool")
    @Operation(summary = "放入公海")
    @PostMapping("/{id}/to-pool")
    public CommonResult<Boolean> toPool(@PathVariable("id") Long id,
                                        @RequestBody(required = false) ToPoolReqVO reqVO) {
        customerService.putToPool(id, reqVO);
        return CommonResult.success(true);
    }

    /**
     * 关注 / 取消关注，路由与 lead 一对对齐（POST + DELETE，非 toggle，保证幂等）。
     */
    @SaCheckPermission("crm:customer:list")
    @Operation(summary = "关注客户")
    @PostMapping("/{id}/star")
    public CommonResult<Boolean> star(@PathVariable("id") Long id) {
        customerService.star(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:customer:list")
    @Operation(summary = "取消关注客户")
    @DeleteMapping("/{id}/star")
    public CommonResult<Boolean> unstar(@PathVariable("id") Long id) {
        customerService.unstar(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:customer:export")
    @Operation(summary = "导出客户（Excel → OSS，返回下载 URL）")
    @GetMapping("/export")
    public CommonResult<String> export(CustomerPageReqVO reqVO) {
        return CommonResult.success(customerService.export(reqVO));
    }

    @SaCheckPermission("crm:customer:export")
    @Operation(summary = "导出客户（XML/SpreadsheetML → OSS，返回下载 URL）")
    @GetMapping("/export-xml")
    public CommonResult<String> exportXml(CustomerPageReqVO reqVO) {
        return CommonResult.success(customerService.exportXml(reqVO));
    }
}
