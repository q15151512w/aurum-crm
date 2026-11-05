package com.aurum.crm.module.crm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.DocumentItemSaveReqVO;
import com.aurum.crm.module.crm.model.dto.OpportunityPageReqVO;
import com.aurum.crm.module.crm.model.dto.OpportunitySaveReqVO;
import com.aurum.crm.module.crm.model.dto.OpportunitySimplePageReqVO;
import com.aurum.crm.module.crm.model.dto.OpportunityStageChangeReqVO;
import com.aurum.crm.module.crm.model.vo.OpportunityFunnelRespVO;
import com.aurum.crm.module.crm.model.vo.OpportunityRespVO;
import com.aurum.crm.module.crm.model.vo.OpportunitySimpleRespVO;
import com.aurum.crm.module.crm.model.vo.OpportunityStageLogRespVO;
import com.aurum.crm.module.crm.service.OpportunityService;
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
 * 商机管理 Controller，对应原型「商机」页与「商机详情」页。
 * 路由见《05-接口文档》5.1 商机。
 *
 * <p><b>本轮实现的 11 个接口</b>：
 * <pre>
 *   GET    /crm/opportunity                 分页查询（含筛选胶囊 scope）
 *   GET    /crm/opportunity/simple-list     选择器专用
 *   GET    /crm/opportunity/funnel          销售漏斗统计
 *   GET    /crm/opportunity/{id}            详情（**含产品明细**）
 *   POST   /crm/opportunity                 新增
 *   PUT    /crm/opportunity/{id}            修改
 *   DELETE /crm/opportunity/{id}            删除
 *   PUT    /crm/opportunity/{id}/stage      变更阶段（写阶段变更日志）
 *   GET    /crm/opportunity/{id}/stage-log  阶段变更历史
 *   POST   /crm/opportunity/{id}/products   **添加产品明细**（追加）
 *   PUT    /crm/opportunity/{id}/products   **更新产品明细**（整份覆盖）
 * </pre>
 *
 * <p><b>产品明细的请求体是裸数组</b>（{@code [ {行}, ... ]}），不是 {@code { "items": [...] }} ——
 * 前端 {@code api/opportunity.js} 就是这么发的（{@code data: products}），
 * 《05-接口文档》5.1 已按实际实现改过来了。
 *
 * <p><b>本轮刻意未实现</b>（原因见 {@code docs/09-商机模块字段契约.md}）：
 * {@code GET /crm/opportunity/export} —— 项目没有 Excel 依赖（与产品模块同一待办）。
 *
 * <p><b>关于路由顺序</b>：{@code /simple-list} 与 {@code /funnel} 必须写在
 * {@code /{id}} 之前（本文件里就是如此）。不过 Spring 的 {@code @GetMapping("/{id}")}
 * 里 {@code id} 声明为 {@code Long}，{@code "simple-list"} 无法绑定成 Long，
 * 会落到更精确的路径匹配上，所以顺序不是硬约束 —— 这里保持「字面量路径在前」只是为了可读性。
 *
 * <p><b>鉴权说明</b>：本模块的 {@code @SaCheckPermission} 已补齐。
 * 权限码全部取自 {@code sql/crm.sql} 的 {@code sys_menu}，不新增权限点：
 * {@code crm:opportunity:list|create|update|delete|changeStage|addProduct|export}。
 *
 * <p>⚠️ 种子数据里没有对应权限点的端点，一律复用<b>语义最近</b>的既有码，不要凭空造新码
 * （新码不在 {@code sys_menu} 里，只会让所有非超管账号 403）：
 * <ul>
 *     <li>详情、{@code /funnel}、阶段历史、关注 / 取消关注 → {@code :list}</li>
 *     <li>{@code PUT /{id}/products}（整份覆盖明细）→ {@code :addProduct}
 *         —— 与 {@code POST} 是同一块「产品明细」功能，没有单独的 replace 权限点</li>
 * </ul>
 * 沿用 {@code CustomerPoolController} 对「没有 export 权限点就用 ：list」的处理先例。
 */
@Tag(name = "商机管理")
@RestController
@RequestMapping("/crm/opportunity")
@Validated
public class OpportunityController {

    @Resource
    private OpportunityService opportunityService;

    @SaCheckPermission("crm:opportunity:list")
    @Operation(summary = "商机分页列表")
    @GetMapping
    public CommonResult<PageResult<OpportunityRespVO>> page(OpportunityPageReqVO reqVO) {
        return CommonResult.success(opportunityService.pageQuery(reqVO));
    }

    @SaCheckPermission("crm:opportunity:list")
    @Operation(summary = "商机选择器")
    @GetMapping("/simple-list")
    public CommonResult<PageResult<OpportunitySimpleRespVO>> simpleList(OpportunitySimplePageReqVO reqVO) {
        return CommonResult.success(opportunityService.simpleList(reqVO));
    }

    @SaCheckPermission("crm:opportunity:list")
    @Operation(summary = "销售漏斗统计（首页看板）")
    @GetMapping("/funnel")
    public CommonResult<List<OpportunityFunnelRespVO>> funnel() {
        return CommonResult.success(opportunityService.funnel());
    }

    @SaCheckPermission("crm:opportunity:export")
    @Operation(summary = "导出商机（Excel → OSS，返回下载 URL）")
    @GetMapping("/export")
    public CommonResult<String> export(OpportunityPageReqVO reqVO) {
        return CommonResult.success(opportunityService.export(reqVO));
    }

    @SaCheckPermission("crm:opportunity:export")
    @Operation(summary = "导出商机（XML/SpreadsheetML → OSS，返回下载 URL）")
    @GetMapping("/export-xml")
    public CommonResult<String> exportXml(OpportunityPageReqVO reqVO) {
        return CommonResult.success(opportunityService.exportXml(reqVO));
    }

    @SaCheckPermission("crm:opportunity:list")
    @Operation(summary = "商机详情（不含产品明细，明细表尚未建表）")
    @GetMapping("/{id}")
    public CommonResult<OpportunityRespVO> get(@PathVariable("id") Long id) {
        return CommonResult.success(opportunityService.get(id));
    }

    @SaCheckPermission("crm:opportunity:create")
    @Operation(summary = "新增商机")
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody OpportunitySaveReqVO reqVO) {
        return CommonResult.success(opportunityService.create(reqVO));
    }

    @SaCheckPermission("crm:opportunity:update")
    @Operation(summary = "修改商机")
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable("id") Long id,
                                        @Valid @RequestBody OpportunitySaveReqVO reqVO) {
        opportunityService.update(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:opportunity:delete")
    @Operation(summary = "删除商机")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        opportunityService.delete(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:opportunity:changeStage")
    @Operation(summary = "变更阶段（会写阶段变更日志）")
    @PutMapping("/{id}/stage")
    public CommonResult<Boolean> changeStage(@PathVariable("id") Long id,
                                             @Valid @RequestBody OpportunityStageChangeReqVO reqVO) {
        opportunityService.changeStage(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:opportunity:addProduct")
    @Operation(summary = "添加产品明细（追加在已有明细之后，请求体为明细行**数组**）")
    @PostMapping("/{id}/products")
    public CommonResult<Boolean> addProducts(@PathVariable("id") Long id,
                                             @Valid @RequestBody List<DocumentItemSaveReqVO> reqVO) {
        opportunityService.addProducts(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:opportunity:addProduct")
    @Operation(summary = "更新产品明细（整份覆盖：先清后插，请求体为明细行**数组**）")
    @PutMapping("/{id}/products")
    public CommonResult<Boolean> replaceProducts(@PathVariable("id") Long id,
                                                 @Valid @RequestBody List<DocumentItemSaveReqVO> reqVO) {
        opportunityService.replaceProducts(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:opportunity:list")
    @Operation(summary = "阶段变更历史（不分页，最近一次在前）")
    @GetMapping("/{id}/stage-log")
    public CommonResult<List<OpportunityStageLogRespVO>> stageLog(@PathVariable("id") Long id) {
        return CommonResult.success(opportunityService.listStageLogs(id));
    }

    /**
     * 关注 / 取消关注。
     *
     * <p>刻意做成 POST + DELETE 一对而不是单个 toggle 接口：toggle 不幂等，
     * 双击或请求重试会把状态翻回去，让图标和库里相反。星标是个裸图标，
     * 恰恰是最容易双击的控件。
     *
     * <p>与线索 / 客户同一形态（{@code POST /crm/lead/{id}/star}）：都返回
     * {@code CommonResult<Boolean>} 的 {@code true}，写入由通用的
     * {@code StarService} + {@code crm_star} 承担。
     */
    @SaCheckPermission("crm:opportunity:list")
    @Operation(summary = "关注商机")
    @PostMapping("/{id}/star")
    public CommonResult<Boolean> star(@PathVariable("id") Long id) {
        opportunityService.star(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:opportunity:list")
    @Operation(summary = "取消关注商机")
    @DeleteMapping("/{id}/star")
    public CommonResult<Boolean> unstar(@PathVariable("id") Long id) {
        opportunityService.unstar(id);
        return CommonResult.success(true);
    }
}
