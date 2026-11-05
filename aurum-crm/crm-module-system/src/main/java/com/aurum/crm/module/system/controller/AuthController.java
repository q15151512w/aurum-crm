package com.aurum.crm.module.system.controller;

import cn.hutool.extra.servlet.JakartaServletUtil;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.module.system.model.dto.ChangePasswordReqVO;
import com.aurum.crm.module.system.model.dto.ForgotPasswordReqVO;
import com.aurum.crm.module.system.model.dto.LoginReqVO;
import com.aurum.crm.module.system.model.dto.ProfileUpdateReqVO;
import com.aurum.crm.module.system.model.dto.RegisterReqVO;
import com.aurum.crm.module.system.model.dto.EmailCodeReqVO;
import com.aurum.crm.module.system.model.dto.EmailLoginReqVO;
import com.aurum.crm.module.system.model.vo.CaptchaRespVO;
import com.aurum.crm.module.system.model.vo.LoginRespVO;
import com.aurum.crm.module.system.model.vo.UserInfoRespVO;
import com.aurum.crm.module.system.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 Controller，对应《05-接口文档》第二章。
 *
 * <p><b>哪些接口需要登录</b>由 {@code SaTokenConfig.EXCLUDE_PATHS} 决定，不在本类里 ——
 * 白名单里的是 {@code /auth/login}、{@code /auth/login/email}、{@code /auth/email/**}、
 * {@code /auth/register}、{@code /auth/password/forgot}；
 * 其余（{@code /auth/me}、{@code /auth/logout}、{@code /auth/profile}、
 * {@code PUT /auth/password}）都必须带 Token。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {

    @Resource
    private AuthService authService;

    @Operation(summary = "密码登录")
    @PostMapping("/login")
    public CommonResult<LoginRespVO> login(@Valid @RequestBody LoginReqVO reqVO,
                                           HttpServletRequest request) {
        return CommonResult.success(authService.login(
                reqVO, JakartaServletUtil.getClientIP(request), userAgentOf(request)));
    }

    @Operation(summary = "获取算术验证码（登录防爆破，开关可关）")
    @GetMapping("/captcha")
    public CommonResult<CaptchaRespVO> captcha() {
        return CommonResult.success(authService.captcha());
    }

    @Operation(summary = "发送邮箱验证码（同一邮箱 60 秒限流）")
    @PostMapping("/email/code")
    public CommonResult<Void> sendEmailCode(@Valid @RequestBody EmailCodeReqVO reqVO) {
        authService.sendEmailCode(reqVO);
        return CommonResult.success();
    }

    @Operation(summary = "邮箱验证码登录")
    @PostMapping("/login/email")
    public CommonResult<LoginRespVO> emailLogin(@Valid @RequestBody EmailLoginReqVO reqVO,
                                                HttpServletRequest request) {
        return CommonResult.success(authService.emailLogin(
                reqVO, JakartaServletUtil.getClientIP(request), userAgentOf(request)));
    }

    @Operation(summary = "注册账号（落地为禁用态，需管理员激活）")
    @PostMapping("/register")
    public CommonResult<Void> register(@Valid @RequestBody RegisterReqVO reqVO) {
        authService.register(reqVO);
        return CommonResult.success();
    }

    @Operation(summary = "忘记密码（邮箱验证码自助重置）")
    @PostMapping("/password/forgot")
    public CommonResult<Void> forgotPassword(@Valid @RequestBody ForgotPasswordReqVO reqVO) {
        authService.forgotPassword(reqVO);
        return CommonResult.success();
    }

    @Operation(summary = "获取当前用户信息（含权限与菜单树）")
    @GetMapping("/me")
    public CommonResult<UserInfoRespVO> me() {
        return CommonResult.success(authService.currentUserInfo());
    }

    @Operation(summary = "修改个人资料（只能改自己）")
    @PutMapping("/profile")
    public CommonResult<Void> updateProfile(@Valid @RequestBody ProfileUpdateReqVO reqVO) {
        authService.updateProfile(reqVO);
        return CommonResult.success();
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public CommonResult<Void> logout() {
        authService.logout();
        return CommonResult.success();
    }

    @Operation(summary = "修改密码（成功后需重新登录）")
    @PutMapping("/password")
    public CommonResult<Void> changePassword(@Valid @RequestBody ChangePasswordReqVO reqVO) {
        authService.changePassword(reqVO);
        return CommonResult.success();
    }

    /**
     * 取 UA 串，供登录日志解析「浏览器 / 操作系统」。
     *
     * <p>只传原始串、不在 Controller 里解析 —— 解析逻辑在
     * {@code UserAgentParser}，认证 Service 只收结果，职责不越界。
     */
    private static String userAgentOf(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
