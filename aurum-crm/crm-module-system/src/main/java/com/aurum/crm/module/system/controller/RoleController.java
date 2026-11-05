package com.aurum.crm.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.model.dto.RolePageReqVO;
import com.aurum.crm.module.system.model.dto.RolePermissionReqVO;
import com.aurum.crm.module.system.model.dto.RoleSaveReqVO;
import com.aurum.crm.module.system.model.dto.RoleUserReqVO;
import com.aurum.crm.module.system.model.vo.RoleRespVO;
import com.aurum.crm.module.system.model.vo.UserRespVO;
import com.aurum.crm.module.system.service.RoleService;
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

import java.util.List;

/**
 * 角色配置（接口文档 3.2）。
 *
 * <p>⚠️ 与人员管理同理：{@code sys_role} 不在数据权限白名单里，
 * 这些方法上的 {@code @SaCheckPermission} 是唯一防线。而角色能改权限配置本身，
 * 漏标一个就是「自己给自己提权」的入口。
 *
 * <p>两组不对称的契约（都是前端既有写法，别统一）：
 * <ul>
 *     <li>查权限返回<b>裸数组</b>，存权限提交<b>对象</b> {@code {menuIds}}</li>
 *     <li>移除人员用 {@code DELETE} 但<b>带 request body</b> {@code {userIds}}</li>
 * </ul>
 */
@Tag(name = "角色配置")
@RestController
@RequestMapping("/system/role")
@Validated
public class RoleController {

    @Resource
    private RoleService roleService;

    @SaCheckPermission("system:role:list")
    @Operation(summary = "角色分页列表（含实时人数）")
    @GetMapping
    public CommonResult<PageResult<RoleRespVO>> page(RolePageReqVO reqVO) {
        return CommonResult.success(roleService.pageQuery(reqVO));
    }

    @SaCheckPermission("system:role:create")
    @Operation(summary = "新增角色")
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody RoleSaveReqVO reqVO) {
        return CommonResult.success(roleService.create(reqVO));
    }

    @SaCheckPermission("system:role:update")
    @Operation(summary = "修改角色")
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable("id") Long id,
                                        @Valid @RequestBody RoleSaveReqVO reqVO) {
        roleService.update(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("system:role:delete")
    @Operation(summary = "删除角色（该角色下还有用户时拒绝）")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        roleService.delete(id);
        return CommonResult.success(true);
    }

    /** 返回裸数组 {@code [1,2,3]}，前端直接 setCheckedKeys */
    @SaCheckPermission("system:role:list")
    @Operation(summary = "查询角色的菜单权限ID集合")
    @GetMapping("/{id}/permissions")
    public CommonResult<List<Long>> permissions(@PathVariable("id") Long id) {
        return CommonResult.success(roleService.listPermissionIds(id));
    }

    /** ⚠️ 请求体是对象 {@code {menuIds:[...]}}，与上面的返回结构不对称 */
    @SaCheckPermission("system:role:assignPermission")
    @Operation(summary = "保存角色的菜单权限")
    @PutMapping("/{id}/permissions")
    public CommonResult<Boolean> savePermissions(@PathVariable("id") Long id,
                                                 @RequestBody RolePermissionReqVO reqVO) {
        roleService.savePermissions(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("system:role:list")
    @Operation(summary = "角色下的人员列表")
    @GetMapping("/{id}/users")
    public CommonResult<PageResult<UserRespVO>> users(@PathVariable("id") Long id,
                                                      RolePageReqVO reqVO) {
        return CommonResult.success(roleService.pageUsers(id, reqVO));
    }

    /** 追加语义：已在该角色下的人不会重复插入 */
    @SaCheckPermission("system:role:assignUser")
    @Operation(summary = "分配人员到角色（追加）")
    @PostMapping("/{id}/users")
    public CommonResult<Boolean> assignUsers(@PathVariable("id") Long id,
                                             @RequestBody RoleUserReqVO reqVO) {
        roleService.assignUsers(id, reqVO);
        return CommonResult.success(true);
    }

    /** ⚠️ DELETE 带 request body —— 前端既有的写法，Spring MVC 可以接住 */
    @SaCheckPermission("system:role:assignUser")
    @Operation(summary = "从角色移除人员")
    @DeleteMapping("/{id}/users")
    public CommonResult<Boolean> removeUsers(@PathVariable("id") Long id,
                                             @RequestBody RoleUserReqVO reqVO) {
        roleService.removeUsers(id, reqVO);
        return CommonResult.success(true);
    }
}
