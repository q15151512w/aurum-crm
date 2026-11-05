package com.aurum.crm.module.crm.ai.tool;

import cn.dev33.satoken.strategy.SaStrategy;
import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.ai.provider.AiToolSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表：收集全部 {@link AiTool} 实现，负责分发、白名单校验与**权限过滤**。
 *
 * <p>用构造注入 {@code List<AiTool>}，加工具只要新建一个 {@code @Component} 类，
 * **不用改本类**。重名在启动时就失败，免得两个工具悄悄抢同一个名字。
 *
 * <p><b>跨模块收集</b>：工具不限于本模块。{@code CrmServerApplication} 在
 * {@code com.aurum.crm} 包下，默认 {@code @ComponentScan} 覆盖全部模块 ——
 * 所以 {@code crm-module-finance} 里实现的工具也会被收集进来
 * （finance 依赖 crm，能拿到 {@link AiTool} 接口；反过来不行，会成环）。
 *
 * <p><b>权限过滤是"每请求"的</b>，不是启动时算一次：{@link #specs()} 每次调用都按
 * **当前登录用户**过滤。工具集随账号而变 —— 超管看到全部，销售只看到自己那摊。
 */
@Slf4j
@Component
public class AiToolRegistry {

    private final Map<String, AiTool> byName;

    public AiToolRegistry(List<AiTool> tools) {
        Map<String, AiTool> map = new LinkedHashMap<>();
        for (AiTool tool : tools) {
            if (map.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalStateException("AI 工具名重复：" + tool.name());
            }
        }
        this.byName = Collections.unmodifiableMap(map);
        log.info("[AI] 已注册 {} 个工具：{}", byName.size(), byName.keySet());
    }

    /**
     * 发给模型的工具清单 —— **只含当前登录用户有权限调用的那些**。
     *
     * <p>这是主要的过滤点，而不是 {@link #execute} 的兜底：被过滤掉的工具，
     * 模型**根本不知道它存在**，也就不会去调它 —— 比"调了再拒绝"体验好得多
     * （后者会让模型先浪费一轮，再对着错误信息编一个解释）。
     *
     * <p>过滤后可能是**空列表**（一个只挂了 dashboard 权限的账号就会这样）。
     * 调用方要处理这种情况，否则模型收到空 tools 会回一句没头没尾的话 ——
     * 见 {@code AiChatServiceImpl} 里给空工具集准备的兜底话术。
     */
    public List<AiToolSpec> specs() {
        return byName.values().stream()
                .filter(tool -> hasPermission(tool.permission()))
                .map(t -> AiToolSpec.of(t.name(), t.description(), t.parameters()))
                .toList();
    }

    /** 当前登录用户实际可用的工具名（供日志/排查，也是 {@link #specs()} 的权威口径）。 */
    public List<String> visibleNames() {
        return byName.values().stream()
                .filter(tool -> hasPermission(tool.permission()))
                .map(AiTool::name)
                .toList();
    }

    /**
     * 执行一个工具。
     *
     * <p><b>入口断言登录上下文</b>（纵深防御）：数据权限拦截器在
     * {@code LoginUserHolder} 为 null 时是**不加任何条件直接放行**的，
     * 且不打日志。也就是说，一旦工具跑在非请求线程上，
     * 用户会静默拿到超出权限范围的数据 —— 不报错、无告警、界面完全正常。
     *
     * <p>所以这里宁可**报错**也不放行：即便将来真有人把链路挪到别的线程，
     * 表现也是「工具执行失败」这样刺眼的错误，而不是一次无声的越权。
     *
     * <p><b>权限在这里再查一次</b>：{@link #specs()} 已经把无权的工具藏起来了，
     * 但模型仍可能幻觉出一个它"记得"的工具名（或响应被篡改）。当前的做法是
     * 「工具名在册就直接执行」，那样这条路径会**绕过权限** —— 所以必须再挡一道。
     */
    public Object execute(String name, Map<String, Object> args) {
        if (LoginUserHolder.get() == null) {
            log.error("[AI] 工具在无登录上下文的线程上执行 name={} —— 已拒绝。" +
                    "这通常意味着有人把 AI 链路挪到了异步线程，导致数据权限失效。", name);
            throw new ServiceException(ErrorCode.AI_SERVICE_UNAVAILABLE, "查询上下文丢失，请重试");
        }
        AiTool tool = byName.get(name);
        if (tool == null) {
            // 正常不会发生：工具清单是我们自己发给上游的。出现即说明上游返回了
            // 未经请求的工具名（模型幻觉或响应被篡改），必须拒绝而不是「尽力执行」。
            throw new ServiceException(ErrorCode.AI_TOOL_NOT_ALLOWED, "不支持的工具：" + name);
        }
        if (!hasPermission(tool.permission())) {
            // 与"工具不存在"分开记日志：这条能让人一眼看出是权限问题，不是模型乱来。
            // ⚠️ 对模型则**统一回同一句话**，不要透露"这个工具存在但你无权"——
            //    那等于变相泄露了系统有哪些能力。
            log.warn("[AI] 用户 {} 尝试调用无权工具 {}（需要 {}）",
                    LoginUserHolder.getUserId(), name, tool.permission());
            throw new ServiceException(ErrorCode.AI_TOOL_NOT_ALLOWED, "不支持的工具：" + name);
        }
        return tool.execute(args == null ? Map.of() : args);
    }

    /**
     * 当前登录用户是否拥有某个权限点。{@code code} 为空白表示该工具不额外要求权限。
     *
     * <p><b>这里刻意不自己写通配匹配，也不用 {@code permissions.contains()}。</b>
     * 直接复用 {@code SaTokenConfig#overridePermissionMatching()} 覆写过的
     * {@link SaStrategy#instance}.hasElement —— 它同时承担两件事：
     * <ul>
     *     <li>{@code *:*:*} 的**超管短路语义**（{@code list.contains(WILDCARD)}）——
     *         注意 {@code *:*:*} 本身不是"匹配一切"的内建语法，这个语义是显式覆写出来的，
     *         见 {@code SuperPermission} 的类注释与 BUG-003；</li>
     *     <li>{@code crm:lead:*} 这类**部分通配**的 {@code vagueMatch}。</li>
     * </ul>
     * 抄一份 {@code contains} 会同时漏掉这两点。{@code DashboardServiceImpl.shortcuts()}
     * 里那份手写判断就是字面量比较（那是它自己的取舍），别照搬到这里。
     *
     * <p>权限来源用 {@link LoginUserHolder} 而不是 {@code StpUtil.hasPermission}：
     * 二者底层是同一个 {@code hasElement}，但后者还要从 Sa-Token 会话里读权限，
     * 而本类开头已经断言了 {@code LoginUserHolder} 非空 —— 只依赖一个来源，
     * 少一个"会话读不到权限码"的失败面。
     */
    private boolean hasPermission(String code) {
        LoginUser user = LoginUserHolder.get();
        // 没有登录上下文 → 一个工具都不给（fail-closed）。与 execute() 开头那个断言是同一个理由：
        // 没有用户就没有权限可言。⚠️ 这一条要在下面 code 判空**之前**，
        // 否则「不声明权限」的工具会在未登录时照样被列出来 —— 而它一执行就会被 execute() 拒掉，
        // 两张表对不上。
        if (user == null) {
            return false;
        }
        if (code == null || code.isBlank()) {
            // 工具没声明权限 = 仅需登录。走到这里说明已经登录了。
            return true;
        }
        if (user.getPermissions() == null) {
            return false;
        }
        return SaStrategy.instance.hasElement.apply(new ArrayList<>(user.getPermissions()), code);
    }
}
