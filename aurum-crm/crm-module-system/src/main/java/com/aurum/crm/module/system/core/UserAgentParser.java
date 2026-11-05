package com.aurum.crm.module.system.core;

import cn.hutool.http.useragent.Browser;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import org.springframework.util.StringUtils;

/**
 * User-Agent 解析：把请求头里的 UA 串拆成「浏览器」与「操作系统」两个短字符串，
 * 供 {@code sys_login_log} 的 {@code browser} / {@code os} 两列使用。
 *
 * <p><b>为什么单独一个类</b>：解析逻辑（含各种空值兜底）放在认证主干里会淹没
 * 「先验验证码再验密码」这些真正要紧的注释。这里包一层，认证侧只调一个
 * {@link #parse(String)} 拿结果。
 *
 * <p>用 hutool 的 {@link UserAgentUtil} 而不是自己写正则：hutool-all 已是本项目依赖
 * （{@code JakartaServletUtil.getClientIP} 也在用它），不必为此新增库。
 *
 * <p>返回值的形状是 {@code String[]{"浏览器 版本", "操作系统"}}，
 * 两项都可能为 {@code null}（UA 缺失或识别不出来）——
 * <b>识别不出来是常态</b>（爬虫、Postman、自定义客户端），不要当成错误。
 */
public final class UserAgentParser {

    private UserAgentParser() {
    }

    /**
     * 解析 UA。
     *
     * @param userAgent 原始 UA 串，可为 null / 空
     * @return 长度为 2 的数组：{@code [浏览器, 操作系统]}，元素可能为 null
     */
    public static String[] parse(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return new String[]{null, null};
        }
        try {
            UserAgent ua = UserAgentUtil.parse(userAgent);
            if (ua == null) {
                return new String[]{null, null};
            }
            return new String[]{browserOf(ua), osOf(ua)};
        } catch (Exception e) {
            // hutool 解析异常不该影响登录 —— 宁可这两列为空
            return new String[]{null, null};
        }
    }

    /**
     * {@code "Chrome 120"} 这种「名字 + 版本」的形态，便于日志页阅读。
     *
     * <p>⚠️ 版本号取的是 {@code ua.getVersion()} 而<b>不是</b> {@code browser.getVersion()}：
     * hutool 里 {@code Browser.getVersion(String userAgentString)} 是「从 UA 串里现抽版本」的方法，
     * 需要把原始 UA 传进去；而 {@link UserAgent#getVersion()} 才是解析时已经算好的浏览器版本。
     * 写错会得到一个「方法参数不匹配」的编译错误。
     */
    private static String browserOf(UserAgent ua) {
        Browser browser = ua.getBrowser();
        if (browser == null || !StringUtils.hasText(browser.getName())) {
            return null;
        }
        String version = ua.getVersion();
        return StringUtils.hasText(version) ? browser.getName() + " " + version : browser.getName();
    }

    /** 操作系统名（{@code Windows 11} / {@code macOS 14} / {@code Ubuntu}） */
    private static String osOf(UserAgent ua) {
        if (ua.getOs() == null || !StringUtils.hasText(ua.getOs().getName())) {
            return null;
        }
        return ua.getOs().getName();
    }
}
