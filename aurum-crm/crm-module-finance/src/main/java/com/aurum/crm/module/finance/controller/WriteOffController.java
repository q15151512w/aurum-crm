package com.aurum.crm.module.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.finance.model.dto.WriteOffPageReqVO;
import com.aurum.crm.module.finance.model.vo.WriteOffRespVO;
import com.aurum.crm.module.finance.service.WriteOffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 核销 Controller，对应接口文档 7.3 核销。
 *
 * <p>「核销」的数据事实是发票-回款明细（{@code fin_invoice_payment}），由保存发票时落库，
 * 故本节只提供<b>查询</b>端点：把明细平铺成核销记录列表，无需逐张发票点进详情。
 * 文档 7.3 的 POST 独立核销路径已被合并进发票保存，这里不再重复提供写端点。
 *
 * <p>鉴权：核销记录是发票数据的只读视图，复用 {@code fin:invoice:list} 权限。
 */
@Tag(name = "核销")
@RestController
@RequestMapping("/finance/write-off")
@Validated
public class WriteOffController {

    @Resource
    private WriteOffService writeOffService;

    @SaCheckPermission("fin:invoice:list")
    @Operation(summary = "核销记录分页列表")
    @GetMapping
    public CommonResult<PageResult<WriteOffRespVO>> page(WriteOffPageReqVO reqVO) {
        return CommonResult.success(writeOffService.pageQuery(reqVO));
    }
}
