package com.aurum.crm.common.core;

/**
 * 超级管理员的通配权限标识。
 *
 * <p><b>为什么放在 crm-common 而不是各业务模块</b>：
 * 用它的地方跨了三层 —— 合成权限的登录侧（{@code crm-module-system} 的
 * {@code AuthServiceImpl}）、做权限匹配的框架侧（{@code crm-security} 的
 * {@code SaTokenConfig}）、以及判断快捷入口的看板（{@code crm-module-crm}）。
 * 而 {@code crm-security} 只依赖 {@code crm-common}（见后端 README 的依赖方向说明），
 * 看不到 {@code crm-module-system}，常量留在那边就会被迫在框架里另抄一份、慢慢漂移。
 * 与 {@link DataScope} 同一个理由。
 *
 * <p>⚠️ <b>这个值不在数据库里</b>（{@code sys_menu} 种子里没有这一行），
 * 必须由后端为 admin 角色合成，见 {@code AuthServiceImpl.buildLoginUserContext}。
 *
 * <p>⚠️ <b>{@code *:*:*} 不是「匹配一切」的内建语法</b>，它只是 Sa-Token 的一个普通通配模式，
 * 而模式里的 {@code :} 是字面量 —— 所以它默认只命中<b>三段式</b>权限码
 * （{@code crm:lead:list}），命中不了两段式的 {@code dashboard:view}。
 * 「等价于全部权限」这个语义由 {@code SaTokenConfig#overridePermissionMatching()} 显式保证。
 * 详见 {@code docs/07-测试BUG记录.md} 的 BUG-003。
 */
public final class SuperPermission {

    /** 超管通配权限标识 */
    public static final String WILDCARD = "*:*:*";

    private SuperPermission() {
    }
}
