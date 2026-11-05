package com.aurum.crm.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.model.dto.RoleGroupPageReqVO;
import com.aurum.crm.module.system.model.dto.RoleGroupSaveReqVO;
import com.aurum.crm.module.system.model.vo.RoleGroupRespVO;
import com.aurum.crm.module.system.service.RoleGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色组（接口文档 3.2）。
 *
 * <p>只有三个接口，因为前端只有三个动作（列表 / 新增 / 删除）—— **没有编辑**。
 *
 * <p>⚠️ 权限标识沿用 {@code sys_menu} 种子里已有的两个按钮权限点
 * （{@code system:roleGroup:create} / {@code system:roleGroup:delete}）；
 * 列表复用 {@code system:role:list}（角色组的列表就是角色页的一部分，
 * 种子里也没有单独的 roleGroup:list）。
 *
 * <p>与其余 {@code /system/**} 接口同理：{@code sys_role_group} 不在数据权限白名单里，
 * 这里的 {@code @SaCheckPermission} 是唯一防线。
 */
@Tag(name = "角色组")
@RestController
@RequestMapping("/system/role-group")
@Validated
public class RoleGroupController {

    @Resource
    private RoleGroupService roleGroupService;

    @SaCheckPermission("system:role:list")
    @Operation(summary = "角色组分页列表（含组内角色数）")
    @GetMapping
    public CommonResult<PageResult<RoleGroupRespVO>> page(RoleGroupPageReqVO reqVO) {
        return CommonResult.success(roleGroupService.pageQuery(reqVO));
    }

    @SaCheckPermission("system:roleGroup:create")
    @Operation(summary = "新增角色组")
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody RoleGroupSaveReqVO reqVO) {
        return CommonResult.success(roleGroupService.create(reqVO));
    }

    /** 删除后组内角色的 group_id 会被置回 NULL（变成「未分组」），角色本身不受影响 */
    @SaCheckPermission("system:roleGroup:delete")
    @Operation(summary = "删除角色组（组内角色变为未分组）")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        roleGroupService.delete(id);
        return CommonResult.success(true);
    }
}
