package com.aurum.crm.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.model.dto.LoginLogPageReqVO;
import com.aurum.crm.module.system.model.vo.LoginLogRespVO;
import com.aurum.crm.module.system.service.LoginLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录日志 Controller，对应原型「系统管理 → 登陆日志」页
 * （前端 {@code views/system/log/login.vue}，注意页面标题用的是「登<b>陆</b>」）。
 *
 * <p><b>纯查询页</b>：没有新增/修改/删除。日志由 {@code LoginLogService} 在
 * 登录成功 / 登录失败 / 登出三个时机自动写入，<b>没有任何接口能改日志</b> —— 这是审计的基本要求。
 *
 * <p><b>权限</b>：{@code system:loginLog:list} 已配在 {@code sql/crm.sql} 的种子数据里
 * （menu 74），不需要再补菜单。导出走 {@code system:loginLog:export}（menu 7401），
 * 与其它模块同一套「Excel/XML → OSS → 返回下载 URL」的流程。
 */
@Tag(name = "登陆日志")
@RestController
@RequestMapping("/system/login-log")
@Validated
public class LoginLogController {

    @Resource
    private LoginLogService loginLogService;

    @SaCheckPermission("system:loginLog:list")
    @Operation(summary = "登录日志分页列表（keyword 同时匹配登陆人/工号/IP；空串等于不筛）")
    @GetMapping
    public CommonResult<PageResult<LoginLogRespVO>> page(LoginLogPageReqVO reqVO) {
        return CommonResult.success(loginLogService.pageQuery(reqVO));
    }

    @SaCheckPermission("system:loginLog:export")
    @Operation(summary = "导出登录日志 Excel（上传 OSS，返回下载 URL）")
    @GetMapping("/export")
    public CommonResult<String> export(LoginLogPageReqVO reqVO) {
        return CommonResult.success(loginLogService.export(reqVO));
    }

    @SaCheckPermission("system:loginLog:export")
    @Operation(summary = "导出登录日志 XML/SpreadsheetML（上传 OSS，返回下载 URL）")
    @GetMapping("/export-xml")
    public CommonResult<String> exportXml(LoginLogPageReqVO reqVO) {
        return CommonResult.success(loginLogService.exportXml(reqVO));
    }
}
