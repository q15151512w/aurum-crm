package com.aurum.crm.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.module.system.model.dto.DeptSaveReqVO;
import com.aurum.crm.module.system.model.vo.DeptTreeNodeVO;
import com.aurum.crm.module.system.service.DeptService;
import com.aurum.crm.module.system.service.TreeService;
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
 * 部门（接口文档 3.3）。
 *
 * <p>树的查询走 {@code TreeService}；增删改走 {@code DeptService}。
 * <b>4 个权限标识</b>：{@code system:dept:list/create/update/delete} —— 与菜单
 * （组织架构下的「组织架构」页 {@code menu_id=71}）下的按钮权限点对应。
 * 只有超级管理员（{@code *:*:*}）与显式分配了这些权限点的角色能调用。
 */
@Tag(name = "部门管理")
@RestController
@RequestMapping("/system/dept")
@Validated
public class DeptController {

    @Resource
    private TreeService treeService;
    @Resource
    private DeptService deptService;

    @SaCheckPermission("system:dept:list")
    @Operation(summary = "部门树（节点字段为 id / name / children）")
    @GetMapping("/tree")
    public CommonResult<List<DeptTreeNodeVO>> tree() {
        return CommonResult.success(treeService.deptTree());
    }

    /**
     * 部门树（<b>仅需登录，不校验权限点</b>），供业务表单的<b>选择器</b>用。
     *
     * <p><b>为什么必须单开一个端点</b>：上面的 {@code /tree} 要求
     * {@code system:dept:list}，而这个权限在种子里<b>只授予了超级管理员</b>。
     * 但组织架构树还有一个用途是<b>选择器弹窗左侧的筛选面板</b> ——
     * 见前端 {@code EntitySelectorDialog.vue}，凡配了 {@code withDeptTree} 的
     * 选择器（负责人 / 直属上级 / 部门负责人 / 客户签约人 / 公司签约人 / 角色成员
     * <b>共 6 处</b>）每次打开都会加载它。
     *
     * <p>于是销售角色一打开这些表单就会拿到 403。症状很有迷惑性：
     * 前端 {@code loadDeptTree} 自己 try/catch 降级了，<b>选择器实际还能用</b>，
     * 但 {@code request.js} 会先弹一个权限报错 —— 用户看到的是「选不了负责人」，
     * 而功能其实是好的。这与 {@code /system/user/options}（BUG-018 的同款修复）
     * 是<b>同一个问题的另一条边</b>：那次只修了右侧的人员列表，漏了左侧的树。
     *
     * <p><b>为什么不直接放宽 {@code /tree}</b>：{@code /tree} 同时是「组织架构」
     * 管理页与人员 / 角色表单（选上级部门、选自定义数据范围）的数据源，那些都是
     * 管理员场景，权限边界应当保留。这里另开一个语义明确的「选择器端点」。
     *
     * <p><b>安全性</b>：部门树不含任何敏感信息，且部门名称本来就随
     * {@code UserSimpleRespVO.deptName} 返回给每一个登录用户（见 {@code /system/user/options}）。
     * 「登录」这一关仍由 {@code SaTokenConfig} 的全局拦截器把守
     * （本类不写注解 ≠ 不需要登录）。
     */
    @Operation(summary = "部门树（选择器用，仅需登录）")
    @GetMapping("/options")
    public CommonResult<List<DeptTreeNodeVO>> options() {
        return CommonResult.success(treeService.deptTree());
    }

    @SaCheckPermission("system:dept:create")
    @Operation(summary = "新增部门（ancestors 由服务端按 parentId 计算）")
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody DeptSaveReqVO reqVO) {
        return CommonResult.success(deptService.create(reqVO));
    }

    @SaCheckPermission("system:dept:update")
    @Operation(summary = "修改部门（未传字段保持原值；改了父部门会重算 ancestors）")
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable("id") Long id,
                                        @Valid @RequestBody DeptSaveReqVO reqVO) {
        deptService.update(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("system:dept:delete")
    @Operation(summary = "删除部门（逻辑删除；有子部门或用户时分别抛 1202 / 1203）")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        deptService.delete(id);
        return CommonResult.success(true);
    }
}
