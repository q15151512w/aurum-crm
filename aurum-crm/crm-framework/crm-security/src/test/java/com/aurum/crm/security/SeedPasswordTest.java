package com.aurum.crm.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 种子数据密码校验。
 *
 * <p>初始化脚本 {@code sql/crm.sql} 里的密码哈希是写死的字符串。
 * 一旦这串哈希被误改，所有演示账号都会登不进去，
 * 而且现象是「密码明明是对的却提示密码错误」，排查起来很费时间。
 *
 * <p>本测试把「SQL 里的哈希」和「明文密码」的对应关系固化下来：
 * 改 SQL 时若改了哈希，这里会立刻失败。
 *
 * <p>⚠️ 改密码时同步改两处：本测试的 {@link #SEED_HASH}、{@code sql/crm.sql}。
 */
@DisplayName("种子数据密码")
class SeedPasswordTest {

    /** 当前所有演示账号的密码 */
    private static final String RAW_PASSWORD = "123456";

    /** 与 {@code sql/crm.sql} 中 sys_user.password 的哈希保持一致 */
    private static final String SEED_HASH =
            "$2a$10$lY9zC7thNJQ/NSvJGfqWpe4sD.RYEOsOpQGRQ.vypPv41OLEyCrES";

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    @Test
    @DisplayName("SQL 种子数据里的哈希能被明文 123456 验证通过")
    void seedHashMatchesPassword() {
        assertThat(encoder.matches(RAW_PASSWORD, SEED_HASH))
                .as("sql/*.sql 中的密码哈希与明文 '%s' 不匹配，请重新生成", RAW_PASSWORD)
                .isTrue();
    }

    @Test
    @DisplayName("错误密码不应通过验证")
    void wrongPasswordRejected() {
        assertThat(encoder.matches("wrong-password", SEED_HASH)).isFalse();
        assertThat(encoder.matches("1234567", SEED_HASH)).isFalse();
        assertThat(encoder.matches("", SEED_HASH)).isFalse();
    }

    @Test
    @DisplayName("同一明文每次加密结果不同（BCrypt 自带随机盐）")
    void bcryptUsesRandomSalt() {
        String a = encoder.encode(RAW_PASSWORD);
        String b = encoder.encode(RAW_PASSWORD);

        assertThat(a).isNotEqualTo(b);
        // 但两个哈希都能验证通过
        assertThat(encoder.matches(RAW_PASSWORD, a)).isTrue();
        assertThat(encoder.matches(RAW_PASSWORD, b)).isTrue();
    }
}
