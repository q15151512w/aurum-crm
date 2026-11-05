package com.aurum.crm.module.crm.ai.tool;

import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.core.SuperPermission;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.ai.provider.AiToolSpec;
import com.aurum.crm.security.config.SaTokenConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 工具注册表的**权限过滤**测试 —— 这是 AI 链路的安全边界。
 *
 * <p>背景：加过滤之前，{@code specs()} 把全部工具无条件发给模型、{@code execute()} 也只校验
 * 「工具名在册」，所以**任何登录用户都能让 AI 去查任何领域**（只靠行级数据权限兜底）。
 * 现在工具按 {@code LoginUser.permissions} 过滤，本测试锁住这个行为。
 *
 * <p>⚠️ <b>为什么不 mock {@code SaStrategy}</b>：权限匹配的权威实现在
 * {@code SaTokenConfig#overridePermissionMatching()} 里（它把 {@code *:*:*} 的超管语义
 * 覆写进 {@code SaStrategy.instance.hasElement}）。如果测试里自己造一个匹配函数，
 * 测的就是「测试自己的实现」，而生产那份改坏了不会被发现。
 * 所以这里直接构造 {@code SaTokenConfig} 调**生产的那份覆写方法** ——
 * 依赖只有一个 {@code Environment}（白名单按 profile 拆分后才有的），喂个空的就行。
 * 代价是它会改全局单例，故在 {@link #registerSut} 里统一处理。
 */
class AiToolRegistryTest {

    /** 打桩工具：只关心 name / permission，execute 返回固定串 */
    private record StubTool(String name, String permission) implements AiTool {
        @Override
        public String description() {
            return "测试用工具 " + name;
        }

        @Override
        public Map<String, Object> parameters() {
            return ToolArgs.schema(ToolArgs.props());
        }

        @Override
        public Object execute(Map<String, Object> args) {
            return "ran:" + name;
        }
    }

    private static final AiTool LEAD_SEARCH = new StubTool("search_leads", "crm:lead:list");
    private static final AiTool LEAD_DETAIL = new StubTool("get_lead_detail", "crm:lead:list");
    private static final AiTool PAY_SEARCH = new StubTool("search_payments", "fin:payment:list");
    private static final AiTool LOGIN_LOG = new StubTool("search_login_logs", "system:loginLog:list");
    /** 不声明权限的工具：任何登录用户都该看得到（回归保护 —— 默认实现是 null） */
    private static final AiTool NO_PERM = new StubTool("search_notices", null);

    private AiToolRegistry registry;

    @BeforeEach
    void registerSut() {
        // 用生产的那份覆写，保证 *:*:* 与部分通配的语义与运行时一致。
        // Environment 是 SaTokenConfig 的构造依赖（白名单要按 profile 拆分），
        // 但本测试只用 overridePermissionMatching()，与它无关 —— 给个空的即可。
        new SaTokenConfig(new MockEnvironment()).overridePermissionMatching();
        registry = new AiToolRegistry(List.of(LEAD_SEARCH, LEAD_DETAIL, PAY_SEARCH, LOGIN_LOG, NO_PERM));
    }

    @AfterEach
    void clearContext() {
        LoginUserHolder.clear();
    }

    private void loginWith(String... permissions) {
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        user.setUsername("tester");
        user.setPermissions(new LinkedHashSet<>(List.of(permissions)));
        LoginUserHolder.set(user);
    }

    private List<String> visibleNames() {
        return registry.specs().stream().map(AiToolSpec::getName).toList();
    }

    // ==================== 过滤 ====================

    @Test
    @DisplayName("超管（*:*:*）拿到全部工具")
    void superAdminSeesEverything() {
        loginWith(SuperPermission.WILDCARD);

        assertThat(visibleNames())
                .containsExactlyInAnyOrder("search_leads", "get_lead_detail", "search_payments",
                        "search_login_logs", "search_notices");
    }

    @Test
    @DisplayName("只有 crm:lead:list 时，只拿到声明该权限的两个工具")
    void narrowPermissionSeesSubset() {
        loginWith("crm:lead:list");

        assertThat(visibleNames())
                .containsExactlyInAnyOrder("search_leads", "get_lead_detail", "search_notices");
    }

    @Test
    @DisplayName("部分通配 crm:lead:* 同样生效（走 vagueMatch 那一支）")
    void partialWildcardMatches() {
        loginWith("crm:lead:*");

        assertThat(visibleNames()).contains("search_leads", "get_lead_detail");
    }

    @Test
    @DisplayName("一个业务权限都没有时，工具集为空（只剩不声明权限的那个）")
    void noPermissionSeesOnlyUnrestricted() {
        loginWith("dashboard:view");

        assertThat(visibleNames()).containsExactly("search_notices");
    }

    @Test
    @DisplayName("permissions 为 null 也不炸，按「无权限」处理")
    void nullPermissionsDoesNotBlowUp() {
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        user.setPermissions(null);
        LoginUserHolder.set(user);

        assertThat(visibleNames()).containsExactly("search_notices");
    }

    @Test
    @DisplayName("visibleNames() 与 specs() 口径一致")
    void visibleNamesMatchesSpecs() {
        loginWith("crm:lead:list", "fin:payment:list");

        assertThat(registry.visibleNames()).containsExactlyInAnyOrderElementsOf(visibleNames());
    }

    // ==================== execute 的纵深防御 ====================

    @Test
    @DisplayName("有权限时，execute 正常执行")
    void executeAllowedTool() {
        loginWith("crm:lead:list");

        assertThat(registry.execute("search_leads", Map.of())).isEqualTo("ran:search_leads");
    }

    @Test
    @DisplayName("无权工具：即使名字在册也拒绝（模型可能幻觉出它「记得」的工具名）")
    void executeDeniedWithoutPermission() {
        loginWith("crm:lead:list");

        assertThatThrownBy(() -> registry.execute("search_login_logs", Map.of()))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(ErrorCode.AI_TOOL_NOT_ALLOWED.getCode()));
    }

    @Test
    @DisplayName("不存在的工具名：同样拒绝")
    void executeUnknownTool() {
        loginWith(SuperPermission.WILDCARD);

        assertThatThrownBy(() -> registry.execute("delete_everything", Map.of()))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("无登录上下文：拒绝而不是静默放行（数据权限拦截器在无上下文时会放行一切）")
    void executeWithoutLoginContextIsRejected() {
        // 刻意不调 loginWith —— 模拟链路被挪到非请求线程
        LoginUserHolder.clear();

        assertThatThrownBy(() -> registry.execute("search_leads", Map.of()))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode())
                        .isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE.getCode()));
    }

    @Test
    @DisplayName("params 为 null 时按空参数执行，不抛 NPE")
    void executeWithNullArgs() {
        loginWith("crm:lead:list");

        assertThat(registry.execute("search_leads", null)).isEqualTo("ran:search_leads");
    }

    // ==================== 启动期校验 ====================

    @Test
    @DisplayName("工具名重复时启动即失败，不让两个工具悄悄抢同一个名字")
    void duplicateNameFailsFast() {
        AiTool dup = new StubTool("search_leads", "crm:lead:list");

        assertThatThrownBy(() -> new AiToolRegistry(List.of(LEAD_SEARCH, dup)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("search_leads");
    }

    @Test
    @DisplayName("注册表本身不依赖登录上下文（构造时即可用）")
    void constructionDoesNotRequireLogin() {
        LoginUserHolder.clear();

        assertThat(registry.visibleNames()).isEmpty();
    }

    @Test
    @DisplayName("权限集合是 Set，顺序不影响可见性")
    void permissionOrderIrrelevant() {
        LoginUser u1 = new LoginUser();
        u1.setPermissions(new LinkedHashSet<>(List.of("fin:payment:list", "crm:lead:list")));
        LoginUserHolder.set(u1);
        List<String> a = registry.visibleNames();

        LoginUser u2 = new LoginUser();
        u2.setPermissions(new LinkedHashSet<>(Set.of("crm:lead:list", "fin:payment:list")));
        LoginUserHolder.set(u2);

        assertThat(registry.visibleNames()).containsExactlyInAnyOrderElementsOf(a);
    }
}
