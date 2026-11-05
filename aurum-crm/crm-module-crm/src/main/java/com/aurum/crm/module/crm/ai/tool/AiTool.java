package com.aurum.crm.module.crm.ai.tool;

import java.util.Map;

/**
 * 一个可被大模型调用的工具。
 *
 * <p><b>工具必须是只读的。</b> 本期只提供查询能力，不提供任何写操作 ——
 * 参数由模型决定，写操作的风险敞口和「查一下」完全不是一个量级。
 *
 * <p><b>实现必须复用现有 Service，不要自己写 SQL。</b> 这是整套设计的关键：
 * 现有 Service 走 MyBatis Mapper，而数据权限拦截器（{@code DataPermissionInnerInterceptor}）
 * 会在 SQL 层自动追加 {@code owner_user_id} / {@code dept_id} 条件。
 * 一旦绕过 Service 直接写 SQL（尤其是 JDBC），行级权限就没了，
 * 销售专员问一句「查所有客户」就能拿到全公司的数据。
 *
 * <p>实现类是 {@code @Component}，由 {@link AiToolRegistry} 自动收集 ——
 * 加工具只需要新建一个类，不用改注册表。
 */
public interface AiTool {

    /** 工具名，全局唯一，如 {@code search_leads}。模型按这个名字调用。 */
    String name();

    /**
     * 给模型看的用途说明。
     *
     * <p>写得越具体，模型选错工具的概率越低。建议把「什么时候该用它」也写进去，
     * 而不是只说它查什么。
     */
    String description();

    /** 参数的 JSON Schema（{@code {"type":"object","properties":{...},"required":[...]}}）。 */
    Map<String, Object> parameters();

    /**
     * 调用本工具需要的权限点，如 {@code crm:lead:list}、{@code system:loginLog:list}。
     *
     * <p>{@link AiToolRegistry} 会用它给当前登录用户过滤工具清单：**没有这个权限的人，
     * 模型根本看不到这个工具**，也就不会去调。权限点请从 {@code sys_menu.perms} 里取现成的，
     * 别自己编 —— 编出来的码谁都不匹配，等于把工具对所有人关掉。
     *
     * <p><b>返回 {@code null} 表示不额外要求</b>（仅需登录即可用）。默认实现就是 null，
     * 所以老工具不加这个方法是不会失效的；但除非有明确理由，新工具都应该声明。
     *
     * <p>⚠️ <b>这是函数级权限，不是行级权限。</b> 行级（能看哪些行）由
     * {@code DataPermissionInnerInterceptor} 在 SQL 层自动追加条件，两者是**交集**关系：
     * 工具既要用户有功能权限，查出来的行也仍然受数据权限限制。
     * 反过来，查 {@code sys_user} / {@code sys_login_log} 这类**不在数据权限白名单里**的表时，
     * 行级权限不生效 —— 这个 {@code permission()} 就是唯一的闸门，别写成 null。
     */
    default String permission() {
        return null;
    }

    /**
     * 执行工具。
     *
     * <p>入参是模型给的原始参数 Map，实现自己取值、自己兜默认值
     * （用 {@link ToolArgs} 的静态方法，别直接强转）。
     *
     * <p>返回值必须可被 JSON 序列化，且**必须通过 {@link ToolResultShaper} 包一层信封** ——
     * 模型看不到「一共有多少条」，就分不清「只有 3 条」和「我只看到前 3 条」，
     * 会据此给出错误结论。
     *
     * <p>执行失败请直接抛异常：编排层会捕获并把错误信息回灌给模型让它自己纠正，
     * 不会中断整条对话。
     */
    Object execute(Map<String, Object> args);
}
