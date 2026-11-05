package com.aurum.crm.module.system.service;

import com.aurum.crm.module.system.model.dto.ChangePasswordReqVO;
import com.aurum.crm.module.system.model.dto.EmailCodeReqVO;
import com.aurum.crm.module.system.model.dto.EmailLoginReqVO;
import com.aurum.crm.module.system.model.dto.ForgotPasswordReqVO;
import com.aurum.crm.module.system.model.dto.LoginReqVO;
import com.aurum.crm.module.system.model.dto.ProfileUpdateReqVO;
import com.aurum.crm.module.system.model.dto.RegisterReqVO;
import com.aurum.crm.module.system.model.vo.CaptchaRespVO;
import com.aurum.crm.module.system.model.vo.LoginRespVO;
import com.aurum.crm.module.system.model.vo.UserInfoRespVO;

/**
 * 认证服务，对应《05-接口文档》第二章的 9 个 {@code /auth} 接口。
 */
public interface AuthService {

    /**
     * 密码登录（文档 2.1）。
     *
     * <p>{@code clientIp} 与 {@code userAgent} 都会被写进登录日志（{@code sys_login_log}）。
     * 之所以由 Controller 传进来而不是在 Service 里取 {@code RequestContextHolder}：
     * 与既有的 {@code clientIp} 保持同一种风格，也让 Service 不依赖 Servlet API。
     */
    LoginRespVO login(LoginReqVO reqVO, String clientIp, String userAgent);

    /**
     * 发送邮箱验证码（文档 2.2）。
     *
     * <p>实际逻辑在 {@code EmailCodeService}，这里只是让 Controller 只依赖一个服务入口。
     */
    void sendEmailCode(EmailCodeReqVO reqVO);

    /**
     * 获取图形验证码（文档 2.9）。
     *
     * <p>实际逻辑在 {@code CaptchaService}，同样只是为了让 Controller 只依赖一个服务入口。
     */
    CaptchaRespVO captcha();

    /** 邮箱验证码登录（文档 2.3），响应与密码登录一致 */
    LoginRespVO emailLogin(EmailLoginReqVO reqVO, String clientIp, String userAgent);

    /** 注册账号（文档 2.4）。落地为禁用态，不自动登录 */
    void register(RegisterReqVO reqVO);

    /** 忘记密码（文档 2.5）。未登录自助重置，靠邮箱验证码证明身份 */
    void forgotPassword(ForgotPasswordReqVO reqVO);

    /** 当前用户信息 + 权限 + 菜单树（文档 2.6） */
    UserInfoRespVO currentUserInfo();

    /** 修改个人资料（文档 2.8），只能改自己 */
    void updateProfile(ProfileUpdateReqVO reqVO);

    /** 修改密码（文档 2.7） */
    void changePassword(ChangePasswordReqVO reqVO);

    /** 退出登录 */
    void logout();
}
