package com.aurum.crm.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.model.dto.UnitPageReqVO;
import com.aurum.crm.module.system.model.dto.UnitSaveReqVO;
import com.aurum.crm.module.system.model.vo.UnitRespVO;
import com.aurum.crm.module.system.model.vo.UnitSimpleRespVO;
import com.aurum.crm.module.system.service.UnitService;
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

/**
 * 计量单位 Controller，对应原型「基础设置 → 计量单位」页。
 * 路由见《05-接口文档》3.6，完整契约见《14-计量单位模块接口文档》。
 *
 * <p><b>权限</b>：4 个权限标识（{@code system:unit:list/create/update/delete}）与菜单
 * （{@code menu_id=76}，按钮 7601–7603）都已在 {@code sql/crm.sql} 的种子数据里配好，
 * <b>不需要再补菜单</b>。{@code sys_role_menu} 里只有角色 1（超级管理员）拿到它们，
 * 所以这是<b>管理员专属页面</b>：销售/财务调用会得到 {@code code:403}。
 *
 * <p>{@code @SaCheckPermission} 是<b>真的在生效</b>：{@code SaTokenConfig} 注册的
 * {@code SaInterceptor} 除了校验登录，还会处理这些注解（与本模块其他 Controller 一致）。
 *
 * <p><b>没有 {@code GET /{id}} 详情接口</b>：前端「编辑」直接拿列表行回填表单
 * （{@code openEdit(rows[0])}），而编辑表单只有「单位名称」一个字段 —— 列表里已经有了，
 * 详情接口没有消费方。同理没有批量删除（前端是逐条并发调 {@code DELETE /{id}}）。
 */
@Tag(name = "计量单位")
@RestController
@RequestMapping("/system/unit")
@Validated
public class UnitController {

    @Resource
    private UnitService unitService;

    @SaCheckPermission("system:unit:list")
    @Operation(summary = "计量单位分页列表（keyword 同时匹配名称与编码；scope 等未知参数忽略）")
    @GetMapping
    public CommonResult<PageResult<UnitRespVO>> page(UnitPageReqVO reqVO) {
        return CommonResult.success(unitService.pageQuery(reqVO));
    }

    @SaCheckPermission("system:unit:create")
    @Operation(summary = "新增计量单位（name 必填唯一；code 不传由服务端生成 U+雪花ID）")
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody UnitSaveReqVO reqVO) {
        return CommonResult.success(unitService.create(reqVO));
    }

    @SaCheckPermission("system:unit:update")
    @Operation(summary = "修改计量单位（未传字段保持原值；改名会影响引用它的产品显示）")
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable("id") Long id,
                                       @Valid @RequestBody UnitSaveReqVO reqVO) {
        unitService.update(id, reqVO);
        return CommonResult.success(true);
    }

    /** 被产品 / 商机明细 / 报价单明细 / 合同明细引用时拒绝（{@code 1504}），不做悬空引用 */
    @SaCheckPermission("system:unit:delete")
    @Operation(summary = "删除计量单位（逻辑删除；被产品/单据引用时拒绝）")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        unitService.delete(id);
        return CommonResult.success(true);
    }

    /**
     * 选择器：只返回启用单位。产品新增/编辑表单与商机·报价单·合同的产品明细编辑器
     * （{@code ProductItemEditor}）的单位下拉读这个接口，从而不再需要前端硬编码单位清单。
     */
    @SaCheckPermission("system:unit:list")
    @Operation(summary = "计量单位选择器（只返回启用单位，供产品表单/明细编辑器的单位下拉使用）")
    @GetMapping("/simple-list")
    public CommonResult<PageResult<UnitSimpleRespVO>> simpleList(UnitPageReqVO reqVO) {
        return CommonResult.success(unitService.simplePage(reqVO));
    }
}
