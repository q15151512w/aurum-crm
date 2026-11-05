package com.aurum.crm.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 白名单的 profile 分支测试。
 *
 * <p>背景：接口文档路径（{@code /doc.html}、{@code /webjars/**} 等）原本一直在白名单里，
 * 而 {@code knife4j.enable: false} 只关 Knife4j 的自动配置 Bean，关不掉 jar 包里的
 * 静态资源 —— 于是线上公网可以无凭证打开文档 UI。这里把「prod 必须拿不到那几条路径」
 * 钉成断言，避免哪天有人图省事又加回去。
 */
class SaTokenConfigTest {

    private static final List<String> DOC_PATHS = List.of(
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**");

    private static SaTokenConfig configWithProfiles(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new SaTokenConfig(env);
    }

    private static List<String> excludes(String... profiles) {
        return Arrays.asList(configWithProfiles(profiles).excludePaths());
    }

    @Test
    void prod_doesNotExcludeAnyDocPath() {
        List<String> excluded = excludes("prod");

        assertThat(excluded).as("prod 下接口文档静态资源必须需要登录才能访问")
                .doesNotContainAnyElementsOf(DOC_PATHS);
    }

    @Test
    void dev_stillExcludesDocPaths() {
        // 本地开发要能直接用 Swagger/Knife4j 调试，这条不能被一起收紧
        List<String> excluded = excludes("dev");

        assertThat(excluded).containsAll(DOC_PATHS);
    }

    @Test
    void noProfile_keepsDocPaths() {
        // application.yml 里 ${SPRING_PROFILES_ACTIVE:dev}，不设变量时就是 dev。
        // 判据是「prod 未激活」而非「dev 已激活」，所以空 profile 也要保留文档。
        assertThat(excludes()).containsAll(DOC_PATHS);
    }

    @Test
    void customProfile_keepsDocPaths() {
        // 同事用 local / test 之类自定义 profile 本地起服务时，
        // 不能突然看不到文档 —— 那种失败会以 401 的形式出现，很容易被当成登录问题排查。
        assertThat(excludes("local")).containsAll(DOC_PATHS);
    }

    @Test
    void prodWinsOnlyWhenActuallyActive() {
        // 多 profile 并存时，只要 prod 在里面就必须收紧
        assertThat(excludes("prod", "dev")).doesNotContainAnyElementsOf(DOC_PATHS);
    }

    @Test
    void basePaths_areUnaffectedByProfile() {
        // 收紧的只能是文档路径。登录、验证码这些一旦被误伤，
        // 表现是「所有人都登不进来」，比文档暴露严重得多。
        List<String> base = List.of("/auth/login", "/auth/login/email", "/auth/register",
                "/auth/captcha", "/auth/email/**", "/auth/password/forgot",
                "/favicon.ico", "/error", "/health", "/actuator/**");

        assertThat(excludes("prod")).containsAll(base);
        assertThat(excludes("dev")).containsAll(base);
    }

    @Test
    void prodCeiling_isExact() {
        // 防止「顺手多删一条」—— prod 的白名单必须正好是这 10 条
        assertThat(excludes("prod")).containsExactlyInAnyOrder(
                "/auth/login", "/auth/login/email", "/auth/register",
                "/auth/captcha", "/auth/email/**", "/auth/password/forgot",
                "/favicon.ico", "/error", "/health", "/actuator/**");
    }
}
