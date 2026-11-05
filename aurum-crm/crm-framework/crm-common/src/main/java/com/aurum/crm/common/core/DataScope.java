package com.aurum.crm.common.core;

/**
 * 数据范围取值，对应 {@code sys_role.data_scope}。
 *
 * <p><b>为什么放在 crm-common 而不是各业务模块</b>：
 * 数据权限拦截器在 {@code crm-mybatis} 里，而 {@code crm-mybatis} 只依赖 {@code crm-common}
 * （不依赖 {@code crm-security} 或任何 {@code crm-module-*}，见后端 README 的依赖方向说明）。
 * 常量放这里，算 scope 的登录侧（{@code crm-module-system}）和用 scope 的持久层
 * （{@code crm-mybatis}）才能共用同一份定义，不会各写一套然后慢慢漂移。
 *
 * <p>取值语义见《02-建表文档》与《04-权限设计文档》3.3：
 * <ul>
 *     <li>{@link #ALL} 全部 —— 不追加任何条件</li>
 *     <li>{@link #CUSTOM} 自定义 —— 可见部门取 {@code sys_role_dept}</li>
 *     <li>{@link #DEPT} 本部门</li>
 *     <li>{@link #DEPT_AND_CHILD} 本部门及以下 —— 登录时已按 {@code sys_dept.ancestors} 算好集合</li>
 *     <li>{@link #SELF} 仅本人</li>
 * </ul>
 *
 * <p>⚠️ <b>数值越小范围越宽</b>，所以登录时取多角色中的 {@code min} 而不是 {@code max}。
 */
public final class DataScope {

    /** 全部数据 */
    public static final Integer ALL = 1;

    /** 自定义（取 sys_role_dept） */
    public static final Integer CUSTOM = 2;

    /** 本部门 */
    public static final Integer DEPT = 3;

    /** 本部门及以下 */
    public static final Integer DEPT_AND_CHILD = 4;

    /** 仅本人 */
    public static final Integer SELF = 5;

    private DataScope() {
    }
}
