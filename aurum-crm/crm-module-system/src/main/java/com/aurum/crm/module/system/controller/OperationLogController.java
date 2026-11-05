package com.aurum.crm.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.model.dto.OperationLogPageReqVO;
import com.aurum.crm.module.system.model.vo.OperationLogRespVO;
import com.aurum.crm.module.system.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志 Controller，对应原型「系统管理 → 操作日志」页
 * （前端 {@code views/system/log/operation.vue}）。
 *
 * <p><b>纯查询页</b>：日志由 {@code OperationLogAspect} 自动记录，
 * <b>没有任何接口能改或删日志</b>。
 *
 * <p><b>菜单是隐藏的</b>：{@code sql/crm.sql} 里这条菜单（menu 77）的 {@code visible = 0}，
 * 由「登陆日志」页切 Tab 进入 —— 但接口权限点 {@code system:operationLog:list} 照常校验。
 *
 * <p><b>没有导出</b>：与登录日志同因，见 {@link LoginLogController} 的类注释。
 *
 * <p>⚠️ 本类是 GET-only，所以<b>不会被 {@code OperationLogAspect} 切到</b>
 * （切点只匹配 POST / PUT / DELETE）—— 看日志本身不会产生日志。
 */
@Tag(name = "操作日志")
@RestController
@RequestMapping("/system/operation-log")
@Validated
public class OperationLogController {

    @Resource
    private OperationLogService operationLogService;

    @SaCheckPermission("system:operationLog:list")
    @Operation(summary = "操作日志分页列表（keyword 同时匹配操作人/模块；空串等于不筛）")
    @GetMapping
    public CommonResult<PageResult<OperationLogRespVO>> page(OperationLogPageReqVO reqVO) {
        return CommonResult.success(operationLogService.pageQuery(reqVO));
    }
}
