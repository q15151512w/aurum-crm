package com.aurum.crm.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.model.dto.DictDataPageReqVO;
import com.aurum.crm.module.system.model.dto.DictDataSaveReqVO;
import com.aurum.crm.module.system.model.dto.DictTypePageReqVO;
import com.aurum.crm.module.system.model.dto.DictTypeSaveReqVO;
import com.aurum.crm.module.system.model.vo.DictDataRespVO;
import com.aurum.crm.module.system.model.vo.DictTypeRespVO;
import com.aurum.crm.module.system.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 字典管理 Controller（《05-接口文档》3.5），对应「组织架构 → 字典管理」页。
 *
 * <p><b>权限</b>：{@code system:dict:*} 与菜单（{@code menu_id=78}，按钮 7801–7803）
 * 都已在 {@code sql/crm.sql} 的种子数据里配好，<b>不需要再补菜单</b>。
 * {@code sys_role_menu} 里只有角色 1（超级管理员）拿到它们，所以这是**管理员专属页面**。
 *
 * <p><b>唯一的例外是 {@code /dict-data/batch}</b> —— 它不加权限注解，理由见方法注释。
 *
 * <p><b>路由用连字符（{@code /dict-type} / {@code /dict-data}）而不是
 * {@code /dict/type}</b>：前端的 api 层就是这么写的，两边必须一致。
 */
@Tag(name = "字典管理")
@RestController
@RequestMapping("/system")
@Validated
public class DictController {

    @Resource
    private DictService dictService;

    // ==================== 字典类型 ====================

    @SaCheckPermission("system:dict:list")
    @Operation(summary = "字典类型分页列表（keyword 同时匹配名称与编码）")
    @GetMapping("/dict-type")
    public CommonResult<PageResult<DictTypeRespVO>> pageType(DictTypePageReqVO reqVO) {
        return CommonResult.success(dictService.pageType(reqVO));
    }

    @SaCheckPermission("system:dict:create")
    @Operation(summary = "新增字典类型（type 编码唯一；status 传中文「正常/停用」）")
    @PostMapping("/dict-type")
    public CommonResult<Long> createType(@Valid @RequestBody DictTypeSaveReqVO reqVO) {
        return CommonResult.success(dictService.createType(reqVO));
    }

    /** 编码（{@code type}）不更新 —— 业务代码按它取值 */
    @SaCheckPermission("system:dict:update")
    @Operation(summary = "修改字典类型（type 编码不可改）")
    @PutMapping("/dict-type/{id}")
    public CommonResult<Boolean> updateType(@PathVariable("id") Long id,
                                            @Valid @RequestBody DictTypeSaveReqVO reqVO) {
        dictService.updateType(id, reqVO);
        return CommonResult.success(true);
    }

    /** 下面还有字典项时拒绝（{@code 1305}），不做悬空引用 */
    @SaCheckPermission("system:dict:delete")
    @Operation(summary = "删除字典类型（逻辑删除；下面还有字典项时拒绝）")
    @DeleteMapping("/dict-type/{id}")
    public CommonResult<Boolean> deleteType(@PathVariable("id") Long id) {
        dictService.deleteType(id);
        return CommonResult.success(true);
    }

    // ==================== 字典项 ====================

    @SaCheckPermission("system:dict:list")
    @Operation(summary = "某个字典类型下的字典项分页列表")
    @GetMapping("/dict-data")
    public CommonResult<PageResult<DictDataRespVO>> pageData(DictDataPageReqVO reqVO) {
        return CommonResult.success(dictService.pageData(reqVO));
    }

    @SaCheckPermission("system:dict:create")
    @Operation(summary = "新增字典项（同类型下键值不可重复）")
    @PostMapping("/dict-data")
    public CommonResult<Long> createData(@Valid @RequestBody DictDataSaveReqVO reqVO) {
        return CommonResult.success(dictService.createData(reqVO));
    }

    /** 所属类型（{@code dictType}）不更新 —— 不允许把一项从 A 字典挪到 B 字典 */
    @SaCheckPermission("system:dict:update")
    @Operation(summary = "修改字典项（所属字典类型不可改）")
    @PutMapping("/dict-data/{id}")
    public CommonResult<Boolean> updateData(@PathVariable("id") Long id,
                                            @Valid @RequestBody DictDataSaveReqVO reqVO) {
        dictService.updateData(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("system:dict:delete")
    @Operation(summary = "删除字典项（逻辑删除）")
    @DeleteMapping("/dict-data/{id}")
    public CommonResult<Boolean> deleteData(@PathVariable("id") Long id) {
        dictService.deleteData(id);
        return CommonResult.success(true);
    }

    /**
     * 按类型批量取启用的字典项。
     *
     * <p><b>⚠️ 刻意不加 {@code @SaCheckPermission}</b>：这条是给**所有登录用户**用的
     * —— 前端拿它把字典缓存下来构造下拉选项。而 {@code system:dict:list} 在种子里
     * 只授给了超管，加上去会变成「只有管理员能看到下拉选项」。
     *
     * <p>与 {@code GET /system/user/options}（人员选择器）是同一套处理：
     * 不加注解 ≠ 不需要登录 —— {@code SaTokenConfig} 注册的全局拦截器照样要求登录态。
     * 这也符合本项目的既有取舍：字典内容是全局共享的配置数据，不是敏感数据。
     */
    @Operation(summary = "批量获取字典项（仅需登录，供前端缓存下拉选项）")
    @GetMapping("/dict-data/batch")
    public CommonResult<Map<String, List<DictDataRespVO>>> dataBatch(
            @RequestParam(value = "types", required = false) String types) {
        // 前端按 `?types=a,b,c` 传；空参返回空 Map 而不是报错 ——
        // 调用方是应用启动时的缓存预热，没有类型要拉不该算失败
        List<String> typeList = StringUtils.hasText(types)
                ? Arrays.stream(types.split(",")).map(String::trim).toList()
                : List.of();
        return CommonResult.success(dictService.dataByTypes(typeList));
    }
}
