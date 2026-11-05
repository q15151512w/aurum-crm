package com.aurum.crm.module.crm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.LeadPageReqDTO;
import com.aurum.crm.module.crm.model.dto.LeadSaveReqDTO;
import com.aurum.crm.module.crm.model.dto.LeadSimplePageReqDTO;
import com.aurum.crm.module.crm.model.dto.ToLeadPoolReqDTO;
import com.aurum.crm.module.crm.model.vo.LeadRespVO;
import com.aurum.crm.module.crm.model.vo.LeadSimpleRespVO;
import com.aurum.crm.module.crm.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 线索管理 Controller，对应原型「线索管理」页。
 * 路由见《接口文档》4.1 线索模块；线索池路由在 {@link LeadPoolController}。
 */
@Tag(name = "线索管理")
@RestController
@RequestMapping("/crm/lead")
@Validated
public class LeadController {

    @Resource
    private LeadService leadService;

    @SaCheckPermission("crm:lead:list")
    @Operation(summary = "线索分页列表")
    @GetMapping
    public CommonResult<PageResult<LeadRespVO>> page(LeadPageReqDTO reqDTO) {
        return CommonResult.success(leadService.pageQuery(reqDTO));
    }

    @SaCheckPermission("crm:lead:list")
    @Operation(summary = "线索选择器")
    @GetMapping("/simple-list")
    public CommonResult<PageResult<LeadSimpleRespVO>> simpleList(LeadSimplePageReqDTO reqDTO) {
        return CommonResult.success(leadService.simpleList(reqDTO));
    }

    @SaCheckPermission("crm:lead:export")
    @Operation(summary = "导出线索（Excel → OSS，返回下载 URL）")
    @GetMapping("/export")
    public CommonResult<String> export(LeadPageReqDTO reqDTO) {
        return CommonResult.success(leadService.export(reqDTO));
    }

    @SaCheckPermission("crm:lead:export")
    @Operation(summary = "导出线索（XML/SpreadsheetML → OSS，返回下载 URL）")
    @GetMapping("/export-xml")
    public CommonResult<String> exportXml(LeadPageReqDTO reqDTO) {
        return CommonResult.success(leadService.exportXml(reqDTO));
    }

    @SaCheckPermission("crm:lead:list")
    @Operation(summary = "线索详情")
    @GetMapping("/{id}")
    public CommonResult<LeadRespVO> get(@PathVariable("id") Long id) {
        return CommonResult.success(leadService.get(id));
    }

    @SaCheckPermission("crm:lead:create")
    @Operation(summary = "新增线索")
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody LeadSaveReqDTO reqDTO) {
        return CommonResult.success(leadService.create(reqDTO));
    }

    @SaCheckPermission("crm:lead:update")
    @Operation(summary = "修改线索")
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable("id") Long id,
                                        @Valid @RequestBody LeadSaveReqDTO reqDTO) {
        leadService.update(id, reqDTO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:lead:delete")
    @Operation(summary = "删除线索")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        leadService.delete(id);
        return CommonResult.success(true);
    }

    /**
     * 批量删除，路由与分页 / 新增同为 {@code /crm/lead}，靠 DELETE 方法与请求体区分。
     *
     * <p>请求体是**裸的 id 数组**（如 {@code [1,2,3]}），与前端
     * {@code batchDeleteLead} 的实现对齐 —— 前端列表页的「删除」按钮走的就是这里，
     * 删一条也是走它。
     */
    @SaCheckPermission("crm:lead:delete")
    @Operation(summary = "批量删除线索")
    @DeleteMapping
    public CommonResult<Boolean> deleteBatch(@RequestBody(required = false) List<Long> ids) {
        leadService.deleteBatch(ids);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:lead:toPool")
    @Operation(summary = "放入线索池")
    @PostMapping("/{id}/to-pool")
    public CommonResult<Boolean> toPool(@PathVariable("id") Long id,
                                        @RequestBody(required = false) ToLeadPoolReqDTO reqDTO) {
        leadService.putToPool(id, reqDTO);
        return CommonResult.success(true);
    }

    /**
     * 关注 / 取消关注。
     *
     * <p>刻意做成 POST + DELETE 一对而不是单个 toggle 接口：toggle 不幂等，
     * 双击或请求重试会把状态翻回去，让图标和库里相反。星标是个裸图标，
     * 恰恰是最容易双击的控件。
     */
    @SaCheckPermission("crm:lead:list")
    @Operation(summary = "关注线索")
    @PostMapping("/{id}/star")
    public CommonResult<Boolean> star(@PathVariable("id") Long id) {
        leadService.star(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:lead:list")
    @Operation(summary = "取消关注线索")
    @DeleteMapping("/{id}/star")
    public CommonResult<Boolean> unstar(@PathVariable("id") Long id) {
        leadService.unstar(id);
        return CommonResult.success(true);
    }

    /**
     * 线索转客户 —— LTC 主线的关键节点。
     *
     * <p>按线索字段生成一条客户记录（{@code crm_customer.lead_id} 回填溯源），
     * 并在线索上标记已转化。转化幂等由 {@code LEAD_ALREADY_CONVERTED} 兜底。
     *
     * @return 新生成客户的 ID
     */
    @SaCheckPermission("crm:lead:convert")
    @Operation(summary = "线索转客户")
    @PostMapping("/{id}/convert")
    public CommonResult<Long> convert(@PathVariable("id") Long id) {
        return CommonResult.success(leadService.convert(id));
    }
}
