package com.aurum.crm.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.module.system.model.vo.MenuTreeVO;
import com.aurum.crm.module.system.service.TreeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单（接口文档 3.4）。
 *
 * <p>本期只实现「树」—— 角色配置页的权限勾选树要它，返回**全部可授权菜单**。
 * 菜单的增删改不在本次范围内。
 */
@Tag(name = "菜单管理")
@RestController
@RequestMapping("/system/menu")
@Validated
public class MenuController {

    @Resource
    private TreeService treeService;

    /**
     * 权限标识用 {@code system:role:assignPermission} 而不是 {@code system:menu:list} ——
     * 这棵树唯一的用途就是给角色配权限，能看到它就等于能改权限，
     * 所以按「分配权限」的权限来管更贴切（种子里也没有 menu:list 这个权限点）。
     */
    @SaCheckPermission("system:role:assignPermission")
    @Operation(summary = "菜单树（全量，供角色配置勾选权限）")
    @GetMapping("/tree")
    public CommonResult<List<MenuTreeVO>> tree() {
        return CommonResult.success(treeService.menuTree());
    }
}
