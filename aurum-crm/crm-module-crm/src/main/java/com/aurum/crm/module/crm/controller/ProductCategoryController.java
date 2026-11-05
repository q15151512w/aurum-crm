package com.aurum.crm.module.crm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.ProductCategoryPageReqVO;
import com.aurum.crm.module.crm.model.dto.ProductCategorySaveReqVO;
import com.aurum.crm.module.crm.model.vo.ProductCategoryRespVO;
import com.aurum.crm.module.crm.service.ProductCategoryService;
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
 * 产品分类 Controller，对应原型「产品类型」页的左侧分类树。
 * 路由见《05-接口文档》5.3 产品与分类。
 *
 * <p><b>鉴权</b>：{@code @SaCheckPermission} 已补齐，权限码取自 {@code sql/crm.sql} 的
 * {@code sys_menu}：{@code crm:productCategory:list|create|update|delete}。
 * 种子里没有 {@code :export}，两个导出端点复用 {@code :list}。
 * 写操作与产品一样<b>只有超管</b>被授权（见 {@link ProductController} 的说明）。
 */
@Tag(name = "产品分类")
@RestController
@RequestMapping("/crm/product-category")
@Validated
public class ProductCategoryController {

    @Resource
    private ProductCategoryService productCategoryService;

    @SaCheckPermission("crm:productCategory:list")
    @Operation(summary = "产品类型列表（扁平分页；路径名沿用 tree，前端按 {list,total} 消费）")
    @GetMapping("/tree")
    public CommonResult<PageResult<ProductCategoryRespVO>> tree(ProductCategoryPageReqVO reqVO) {
        return CommonResult.success(productCategoryService.pageList(reqVO));
    }

    @SaCheckPermission("crm:productCategory:list")
    @Operation(summary = "导出产品分类（生成 Excel 上传 OSS，返回下载 URL）")
    @GetMapping("/export")
    public CommonResult<String> export(ProductCategoryPageReqVO reqVO) {
        return CommonResult.success(productCategoryService.export(reqVO));
    }

    @SaCheckPermission("crm:productCategory:list")
    @Operation(summary = "导出产品分类（生成 XML/SpreadsheetML 上传 OSS，返回下载 URL）")
    @GetMapping("/export-xml")
    public CommonResult<String> exportXml(ProductCategoryPageReqVO reqVO) {
        return CommonResult.success(productCategoryService.exportXml(reqVO));
    }

    @SaCheckPermission("crm:productCategory:create")
    @Operation(summary = "新增产品分类")
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody ProductCategorySaveReqVO reqVO) {
        return CommonResult.success(productCategoryService.create(reqVO));
    }

    @SaCheckPermission("crm:productCategory:update")
    @Operation(summary = "修改产品分类")
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable("id") Long id,
                                        @Valid @RequestBody ProductCategorySaveReqVO reqVO) {
        productCategoryService.update(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:productCategory:delete")
    @Operation(summary = "删除产品分类（有子分类或被产品引用时拒绝）")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        productCategoryService.delete(id);
        return CommonResult.success(true);
    }
}
