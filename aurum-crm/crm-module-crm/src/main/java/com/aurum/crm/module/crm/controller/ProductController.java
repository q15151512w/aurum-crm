package com.aurum.crm.module.crm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.ProductPageReqVO;
import com.aurum.crm.module.crm.model.dto.ProductSaveReqVO;
import com.aurum.crm.module.crm.model.dto.ProductSimplePageReqVO;
import com.aurum.crm.module.crm.model.vo.ProductImageRespVO;
import com.aurum.crm.module.crm.model.vo.ProductRespVO;
import com.aurum.crm.module.crm.model.vo.ProductSimpleRespVO;
import com.aurum.crm.module.crm.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * 产品管理 Controller，对应原型「产品类型 → 产品」页。
 * 路由见《05-接口文档》5.3 产品与分类。
 *
 * <p><b>鉴权说明</b>：本模块的 {@code @SaCheckPermission} 已补齐，
 * 权限码取自 {@code sql/crm.sql} 的 {@code sys_menu}：
 * {@code crm:product:list|create|update|delete|onShelf|offShelf}。
 *
 * <p>⚠️ <b>产品写操作的授权面很窄</b>：种子数据里<b>只有超管</b>拥有
 * {@code crm:product:create|update|delete|onShelf|offShelf} ——
 * 销售总监 / 销售专员对产品只有 {@code :list}，财务则完全没有产品菜单。
 * 这是有意的（产品是全局目录，不该由销售随意改价），不是漏配授权。
 *
 * <p>⚠️ 种子里没有 {@code crm:product:export}，两个导出端点复用 {@code :list}；
 * {@code POST /image} 服务于新增与修改两种场景、二者没有共同的更精确权限点，
 * 这里取 {@code :update}。
 */
@Tag(name = "产品管理")
@RestController
@RequestMapping("/crm/product")
@Validated
public class ProductController {

    @Resource
    private ProductService productService;

    @SaCheckPermission("crm:product:list")
    @Operation(summary = "产品分页列表")
    @GetMapping
    public CommonResult<PageResult<ProductRespVO>> page(ProductPageReqVO reqVO) {
        return CommonResult.success(productService.pageQuery(reqVO));
    }

    @SaCheckPermission("crm:product:list")
    @Operation(summary = "产品选择器（默认只返回上架产品）")
    @GetMapping("/simple-list")
    public CommonResult<PageResult<ProductSimpleRespVO>> simpleList(ProductSimplePageReqVO reqVO) {
        return CommonResult.success(productService.simpleList(reqVO));
    }

    @SaCheckPermission("crm:product:list")
    @Operation(summary = "导出产品（生成 Excel 上传 OSS，返回下载 URL）")
    @GetMapping("/export")
    public CommonResult<String> export(ProductPageReqVO reqVO) {
        return CommonResult.success(productService.export(reqVO));
    }

    @SaCheckPermission("crm:product:list")
    @Operation(summary = "导出产品（生成 XML/SpreadsheetML 上传 OSS，返回下载 URL）")
    @GetMapping("/export-xml")
    public CommonResult<String> exportXml(ProductPageReqVO reqVO) {
        return CommonResult.success(productService.exportXml(reqVO));
    }

    @SaCheckPermission("crm:product:list")
    @Operation(summary = "产品详情")
    @GetMapping("/{id}")
    public CommonResult<ProductRespVO> get(@PathVariable("id") Long id) {
        return CommonResult.success(productService.get(id));
    }

    @SaCheckPermission("crm:product:create")
    @Operation(summary = "新增产品")
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody ProductSaveReqVO reqVO) {
        return CommonResult.success(productService.create(reqVO));
    }

    @SaCheckPermission("crm:product:update")
    @Operation(summary = "上传产品图片（返回 OSS URL，填进 image 字段）")
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<ProductImageRespVO> uploadImage(
            @Parameter(description = "图片文件（form-data 字段名固定为 file），支持 jpg/jpeg/png/webp/gif，≤5MB")
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return CommonResult.success(productService.uploadImage(file));
    }

    @SaCheckPermission("crm:product:update")
    @Operation(summary = "修改产品")
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable("id") Long id,
                                        @Valid @RequestBody ProductSaveReqVO reqVO) {
        productService.update(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:product:delete")
    @Operation(summary = "删除产品")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        productService.delete(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:product:onShelf")
    @Operation(summary = "上架")
    @PutMapping("/{id}/on-shelf")
    public CommonResult<Boolean> onShelf(@PathVariable("id") Long id) {
        productService.onShelf(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:product:offShelf")
    @Operation(summary = "下架")
    @PutMapping("/{id}/off-shelf")
    public CommonResult<Boolean> offShelf(@PathVariable("id") Long id) {
        productService.offShelf(id);
        return CommonResult.success(true);
    }
}
