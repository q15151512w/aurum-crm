package com.aurum.crm.security.handler;

import cn.dev33.satoken.exception.DisableServiceException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Sa-Token 异常处理器。
 *
 * <p><b>为什么必须单独写一个</b>：crm-common 的 {@code GlobalExceptionHandler}
 * 里有个 {@code @ExceptionHandler(Exception.class)} 兜底分支。如果不在这里
 * 抢先处理 Sa-Token 的异常，「未登录」会被当成未知异常返回 <b>500</b>，
 * 前端拿到 500 不会跳登录页，用户看到的是「服务器内部错误」——
 * 这是很典型的排查半天的问题。
 *
 * <p><b>{@code @Order} 的作用</b>：保证本处理器优先级高于兜底处理器。
 *
 * @see com.aurum.crm.common.exception.GlobalExceptionHandler
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SecurityExceptionHandler {

    /**
     * 未登录 / Token 无效 / Token 过期。
     *
     * <p>统一返回 401，前端拦截器据此跳转登录页。
     */
    @ExceptionHandler(NotLoginException.class)
    public CommonResult<Void> handleNotLogin(NotLoginException e) {
        // 细化提示：Sa-Token 会告诉我们具体是哪种未登录
        String message = switch (e.getType()) {
            case NotLoginException.NOT_TOKEN -> "未提供登录凭证，请先登录";
            case NotLoginException.INVALID_TOKEN -> "登录凭证无效，请重新登录";
            case NotLoginException.TOKEN_TIMEOUT -> "登录已过期，请重新登录";
            case NotLoginException.BE_REPLACED -> "账号已在其他设备登录";
            case NotLoginException.KICK_OUT -> "账号已被强制下线";
            default -> "未登录，请先登录";
        };
        log.warn("[认证失败] type={} msg={}", e.getType(), message);
        return CommonResult.error(ErrorCode.UNAUTHORIZED, message);
    }

    /** 缺少权限（@SaCheckPermission 校验不通过） */
    @ExceptionHandler(NotPermissionException.class)
    public CommonResult<Void> handleNotPermission(NotPermissionException e) {
        log.warn("[权限不足] 缺少权限: {}", e.getPermission());
        return CommonResult.error(ErrorCode.FORBIDDEN, "没有该操作权限：" + e.getPermission());
    }

    /** 缺少角色（@SaCheckRole 校验不通过） */
    @ExceptionHandler(NotRoleException.class)
    public CommonResult<Void> handleNotRole(NotRoleException e) {
        log.warn("[角色不足] 缺少角色: {}", e.getRole());
        return CommonResult.error(ErrorCode.FORBIDDEN, "没有该角色权限：" + e.getRole());
    }

    /** 账号被封禁 */
    @ExceptionHandler(DisableServiceException.class)
    public CommonResult<Void> handleDisabled(DisableServiceException e) {
        log.warn("[账号封禁] 剩余时间: {}s", e.getDisableTime());
        return CommonResult.error(ErrorCode.USER_NOT_ENABLED,
                String.format("账号已被封禁，剩余 %d 秒", e.getDisableTime()));
    }

    /** 其他 Sa-Token 异常兜底 */
    @ExceptionHandler(SaTokenException.class)
    public CommonResult<Void> handleSaToken(SaTokenException e) {
        log.warn("[Sa-Token 异常] {}", e.getMessage());
        return CommonResult.error(ErrorCode.UNAUTHORIZED, "认证异常：" + e.getMessage());
    }
}
