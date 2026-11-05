package com.aurum.crm.security.core;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码加密器。
 *
 * <p>用 BCrypt 而不是 MD5/SHA：
 * <ul>
 *     <li>自带随机盐，同一密码每次加密结果不同，彩虹表失效</li>
 *     <li>可调计算强度（cost），随硬件升级可提高</li>
 *     <li>本身就是为密码存储设计的，比「SHA256 + 固定盐」正确</li>
 * </ul>
 *
 * <p><b>为什么单独引 {@code spring-security-crypto} 而不是整个 Spring Security</b>：
 * 只需要一个加密工具类，没必要背一整套过滤器链。
 */
@Component
public class PasswordEncoder {

    /**
     * 计算强度。默认 10（2^10 次迭代）。
     * 数值越大越安全，但登录接口耗时也线性增长 —— 10 大约 50~100ms，是常见平衡点。
     */
    private static final int STRENGTH = 10;

    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder(STRENGTH);

    /** 加密明文密码 */
    public String encode(String rawPassword) {
        return delegate.encode(rawPassword);
    }

    /**
     * 校验密码。
     *
     * <p>注意 BCrypt 不支持「解密」，只能把明文再哈希后比对。
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return delegate.matches(rawPassword, encodedPassword);
    }
}
