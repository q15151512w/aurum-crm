package com.aurum.crm.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.model.dto.NumberingRulePageReqVO;
import com.aurum.crm.module.system.model.dto.NumberingRuleSaveReqVO;
import com.aurum.crm.module.system.model.vo.NumberingRuleRespVO;
import com.aurum.crm.module.system.service.NumberingRuleService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/numbering-rule")
public class NumberingRuleController {
    @Resource private NumberingRuleService service;

    @SaCheckPermission("system:numberingRule:list")
    @GetMapping
    public CommonResult<PageResult<NumberingRuleRespVO>> page(NumberingRulePageReqVO req) {
        return CommonResult.success(service.page(req));
    }

    @SaCheckPermission("system:numberingRule:create")
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody NumberingRuleSaveReqVO req) {
        return CommonResult.success(service.create(req));
    }

    @SaCheckPermission("system:numberingRule:update")
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable Long id, @Valid @RequestBody NumberingRuleSaveReqVO req) {
        service.update(id, req);
        return CommonResult.success(true);
    }

    @SaCheckPermission("system:numberingRule:delete")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success(true);
    }
}
