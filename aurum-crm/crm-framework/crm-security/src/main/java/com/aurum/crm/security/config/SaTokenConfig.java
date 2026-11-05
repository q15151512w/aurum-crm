package com.aurum.crm.security.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.strategy.SaStrategy;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaFoxUtil;
import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.core.SuperPermission;
import com.aurum.crm.security.core.LoginUserKeys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Sa-Token 配置：注册认证拦截器，并把登录用户放进 {@link LoginUserHolder}。
 *
 * <p>拦截链执行顺序：
 * <pre>
 *   SaInterceptor（认证 + 权限注解校验）
 *        ↓
 *   LoginUserInterceptor（把 LoginUser 放进 ThreadLocal）
 *        ↓
 *   Controller
 *        ↓
 *   afterCompletion（清理 ThreadLocal）  ← 必须，否则线程复用会串号
 * </pre>
 */
@Slf4j
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 无需登录即可访问的路径。
     *
     * <p>⚠️ 只列「确实不需要登录」的接口，别图省事写成 {@code /auth/**}
     * —— 那会把 {@code /auth/me}、{@code /auth/logout}、{@code /auth/profile}
     * 一起放开，它们要读登录态，被跳过会让 {@code LoginUserHolder.getRequired()}
     * 抛 IllegalStateException，最终以 HTTP 500 返回，与真实原因完全脱节。
     *
     * <p>注意 {@code /auth/password/forgot} 在白名单里（未登录自助重置），
     * 而 {@code PUT /auth/password}（已登录改密）<b>绝不能</b>加进来 ——
     * 所以只能写精确路径，不能写 {@code /auth/password/**}。
     */
    private static final String[] BASE_EXCLUDE_PATHS = {
            "/auth/login",
            "/auth/login/email",
            "/auth/register",
            // 登录页在未登录状态就要能取验证码图片，所以必须在白名单里。
            // 写精确路径而不是 /auth/captcha/**，避免连带来路不明的子路径
            "/auth/captcha",
            // 发送邮箱验证码：注册 / 找回密码都发生在未登录状态，必须放开。
            // ⚠️ 只放开这一条精确前缀，不要写成 /auth/**
            "/auth/email/**",
            "/auth/password/forgot",
            "/favicon.ico",
            "/error",
            "/health",
            "/actuator/**"
    };

    /**
     * 接口文档相关路径，<b>只在非 prod 环境放开</b>。
     *
     * <p><b>为什么必须按环境分开（2026-09-15 线上实测发现）</b>：
     * 这几条路径原本一直在白名单里，因为开发时要用 Swagger/Knife4j 调试。
     * 但 {@code knife4j.enable: false}（application-prod.yml）<b>只关掉了 Knife4j 的
     * 自动配置 Bean，关不掉 jar 包里的静态资源文件</b> ——
     * {@code knife4j-openapi3-jakarta-spring-boot-starter} 把 UI 放在
     * {@code META-INF/resources/doc.html} 和 {@code META-INF/resources/webjars/**}，
     * 这些由 Spring Boot 默认的静态资源处理器提供，与 Knife4j 的开关毫无关系。
     *
     * <p>于是白名单一放开，公网上任何人都能无凭证拿到：
     * <pre>
     *   GET /doc.html                        → 200，Knife4j UI 页面
     *   GET /webjars/js/app.*.js             → 200，约 396KB
     * </pre>
     *
     * <p>危害程度：接口清单<b>没有</b>泄露 —— {@code /v3/api-docs} 确实返回 404，
     * 打开的只是个空壳 UI。但仍不该对公网开放：白送带宽、对外宣告技术栈，
     * 而且只要哪天有人把 {@code springdoc.api-docs.enabled} 改回 true，
     * 接口清单会<b>立刻</b>跟着这个口子一起暴露出去 —— 一个静默失效的开关不值得留。
     *
     * <p>放开的判据是「prod 未激活」而不是「dev 已激活」：这样除了我们真正加固过的
     * prod，其余环境的现有行为<b>一个字节都不变</b>，改动的影响面和意图完全重合。
     * 反过来写成「只有 dev 才放开」的话，同事用 {@code local} 之类自定义 profile
     * 起本地环境时会突然看不到文档，而且报出来的是 401，会被当成登录问题去排查 ——
     * 静默锁死的代价比多放开一点更大。
     */
    private static final String[] DOC_EXCLUDE_PATHS = {
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**"
    };

    /** 与 {@code deploy/} 下脚本注入的 {@code SPRING_PROFILES_ACTIVE} 同一个值 */
    private static final String PROD_PROFILE = "prod";

    private final Environment environment;

    public SaTokenConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * 按当前激活的 profile 拼出白名单。在 {@code addInterceptors} 里算一次并复用，
     * 保证两个拦截器拿到的是<b>同一份</b>列表 —— 分别计算的话，将来有人在中间改了
     * 逻辑，两条链就会对同一个 URL 给出不同的判断（一个放行一个拦截）。
     *
     * <p>包级可见（而非 private）是为了让同包的测试能直接断言，与
     * {@code GlobalExceptionHandler#sanitize} 的处理一致。
     */
    String[] excludePaths() {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains(PROD_PROFILE);
        if (!prod) {
            return Stream.concat(Arrays.stream(BASE_EXCLUDE_PATHS),
                            Arrays.stream(DOC_EXCLUDE_PATHS))
                    .toArray(String[]::new);
        }
        // prod 下这几条路径不再豁免 —— 走到 SaInterceptor 会被拦下并要求登录。
        // 注意它不会因此返回 404：接口文档的静态资源在 classpath 里真实存在，
        // 只是现在需要凭证才能取。
        return BASE_EXCLUDE_PATHS;
    }

    /**
     * 覆写 Sa-Token 的权限匹配，让超管的通配权限 {@code *:*:*} 真正等价于「拥有全部权限」。
     *
     * <p><b>不覆写会怎样（BUG-003）</b>：Sa-Token 默认的 {@code SaFoxUtil.vagueMatch}
     * 把 {@code *:*:*} 当作<b>普通通配模式</b>来处理，而模式里的 {@code :} 是字面量，
     * 于是它要求目标权限码里<b>至少有两个冒号</b>：
     * <ul>
     *     <li>三段式的 {@code crm:lead:list} → 命中 ✅</li>
     *     <li>两段式的 {@code dashboard:view} → <b>命中不了</b> ❌</li>
     * </ul>
     *
     * <p>而超管的权限集合里<b>只有</b> {@code *:*:*} 这一个值 ——
     * 见 {@code AuthServiceImpl} 的超管分支，它走三元表达式的第一个分支，
     * <b>完全绕过</b> {@code permissionsOf(roleIds)}，所以哪怕
     * {@code sys_role_menu} 里授了全量菜单，也一个具体权限码都拿不到。
     *
     * <p>两者一撞，结果是<b>权限最高的账号唯独进不去首页看板</b>：
     * 因为 {@code dashboard:view} 恰好是全项目唯一的两段式权限点
     * （39 个 {@code @SaCheckPermission} 注解里只有它），其余 38 个三段式的全都正常。
     * 症状与直觉完全相反 —— 权限越低反而越正常。
     *
     * <p>这里显式钉死 {@code *:*:*} 的语义，与前端 {@code user.js} 的
     * {@code hasPermission}（它直接 {@code includes('*:*:*')}）、以及
     * {@code DashboardServiceImpl} 的判断口径对齐，免得以后再有人写出两段式权限码时重蹈覆辙。
     *
     * <p>注意保留 {@code vagueMatch} 这一支：角色被授予 {@code crm:lead:*} 这类
     * 部分通配时的原有行为不受影响，本方法只是<b>追加</b>了超管这一条。
     */
    @PostConstruct
    public void overridePermissionMatching() {
        SaStrategy.instance.hasElement = (list, element) ->
                list != null && (list.contains(SuperPermission.WILDCARD)
                        || list.stream().anyMatch(p -> SaFoxUtil.vagueMatch(p, element)));
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        String[] exclude = excludePaths();

        // 1) Sa-Token 官方拦截器：负责「是否登录」以及 @SaCheckPermission 等注解校验
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns(exclude);

        // 2) 本项目拦截器：把登录用户写入 ThreadLocal，供字段填充与数据权限读取
        registry.addInterceptor(new LoginUserInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(exclude);
    }

    /**
     * 把 Sa-Token 会话里的用户信息读出来放进 {@link LoginUserHolder}。
     */
    @Slf4j
    public static class LoginUserInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(@NonNull HttpServletRequest request,
                                 @NonNull HttpServletResponse response,
                                 @NonNull Object handler) {
            if (StpUtil.isLogin()) {
                LoginUser loginUser = buildLoginUser();
                LoginUserHolder.set(loginUser);
            }
            return true;
        }

        /**
         * ⚠️ 必须清理。
         *
         * <p>Tomcat 线程池会复用线程，ThreadLocal 不清会导致下一个请求读到上一个用户的身份，
         * 这是真实发生过的越权事故。即使 preHandle 抛异常，afterCompletion 也会执行。
         */
        @Override
        public void afterCompletion(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull Object handler,
                                    Exception ex) {
            LoginUserHolder.clear();
        }

        private LoginUser buildLoginUser() {
            LoginUser user = new LoginUser();
            user.setUserId(StpUtil.getLoginIdAsLong());

            SaSession session = StpUtil.getSession();
            Object v;
            if ((v = session.get(LoginUserKeys.USERNAME)) != null) {
                user.setUsername(v.toString());
            }
            if ((v = session.get(LoginUserKeys.REAL_NAME)) != null) {
                user.setRealName(v.toString());
            }
            if ((v = session.get(LoginUserKeys.DEPT_ID)) != null) {
                user.setDeptId(Long.valueOf(v.toString()));
            }
            if ((v = session.get(LoginUserKeys.DATA_SCOPE)) != null) {
                user.setDataScope(Integer.valueOf(v.toString()));
            }

            // 下面三个是集合，由登录接口（AuthServiceImpl.writeSession）写入。
            //
            // ⚠️ 不能沿用上面的 `Long.valueOf(v.toString())` 写法：Session 里存的是 Set，
            //    toString() 出来是 "[sales, finance]"，转数字直接抛 NumberFormatException。
            //    更坑的是它只在【登录后的第一个请求】炸，很容易误判成登录本身有问题。
            user.setRoleKeys(toStringSet(session.get(LoginUserKeys.ROLE_KEYS)));
            user.setPermissions(toStringSet(session.get(LoginUserKeys.PERMISSIONS)));
            user.setDataScopeDeptIds(toLongSet(session.get(LoginUserKeys.DATA_SCOPE_DEPT_IDS)));
            return user;
        }

        /**
         * 把 Session 里取出的集合转成 {@code Set<String>}。
         *
         * <p>逐个 {@code toString()} 而不是强转 —— 经 Redis(Jackson) 反序列化后，
         * 元素类型可能是 String / Integer / Long，强转会在换序列化器时静默失效。
         */
        private static Set<String> toStringSet(Object v) {
            if (!(v instanceof Collection<?> c)) {
                return new LinkedHashSet<>();
            }
            Set<String> result = new LinkedHashSet<>(c.size());
            for (Object e : c) {
                if (e != null) {
                    result.add(e.toString());
                }
            }
            return result;
        }

        /** 把 Session 里取出的集合转成 {@code Set<Long>}，非法元素跳过而不是整条请求失败 */
        private static Set<Long> toLongSet(Object v) {
            if (!(v instanceof Collection<?> c)) {
                return new LinkedHashSet<>();
            }
            Set<Long> result = new LinkedHashSet<>(c.size());
            for (Object e : c) {
                if (e == null) {
                    continue;
                }
                try {
                    result.add(Long.valueOf(e.toString()));
                } catch (NumberFormatException ignored) {
                    log.warn("[登录上下文] 跳过非法部门ID: {}", e);
                }
            }
            return result;
        }
    }
}
