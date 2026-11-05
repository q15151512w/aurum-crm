package com.aurum.crm.common.core;

import java.util.Optional;

/**
 * 登录用户的 ThreadLocal 容器。
 *
 * <p>由 crm-security 的拦截器在请求进入时写入、请求结束时清除；
 * 业务代码与 crm-mybatis 的字段填充从这里读取。
 *
 * <p><b>⚠️ 内存泄漏防范</b>：ThreadLocal 在 Tomcat 线程池下会被复用，
 * 如果不在请求结束时 {@code remove()}，下一个请求会读到上一个用户的身份 ——
 * 这是实际发生过的越权事故。所以必须用 try/finally 清理。
 */
public final class LoginUserHolder {

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    private LoginUserHolder() {
    }

    public static void set(LoginUser loginUser) {
        CONTEXT.set(loginUser);
    }

    /**
     * 获取当前登录用户，未登录返回 {@code null}。
     * 用于「可能没有登录上下文」的场景（如定时任务、字段填充）。
     */
    public static LoginUser get() {
        return CONTEXT.get();
    }

    /**
     * 获取当前登录用户，未登录直接抛异常。
     * 用于「必须有登录上下文」的业务代码。
     */
    public static LoginUser getRequired() {
        return Optional.ofNullable(CONTEXT.get())
                .orElseThrow(() -> new IllegalStateException(
                        "当前线程没有登录上下文，请检查是否在 Web 请求线程中调用"));
    }

    /** 当前用户 ID，未登录返回 {@code null} */
    public static Long getUserId() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getUserId();
    }

    /** 当前用户部门 ID，未登录返回 {@code null} */
    public static Long getDeptId() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getDeptId();
    }

    /** 必须放在 finally 中调用 */
    public static void clear() {
        CONTEXT.remove();
    }
}
