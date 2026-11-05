package com.aurum.crm.module.system.service.impl;

import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.system.core.AuthConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邮箱验证码「连续失败次数上限」的 Java 侧分支测试。
 *
 * <p>⚠️ <b>覆盖范围的诚实说明</b>：真正的防爆破逻辑写在
 * {@code EmailCodeServiceImpl#VERIFY_SCRIPT} 那段 Lua 里，由 Redis 原子执行。
 * 本测试把 {@code StringRedisTemplate} 换成 mock，只验证「拿到 Lua 的返回值之后 Java 怎么反应」
 * —— 也就是 {@code -2} 是否映射成 {@code 1011}。<b>Lua 脚本本身没有被任何测试执行</b>
 * （跑它需要真实 Redis；本机开发环境没有）。所以改动那段脚本后，务必手工验一次：
 * <pre>
 *   # 起后端（连上 Redis）后，对同一邮箱连发 6 次错误的 /auth/login/email，
 *   # 或直接在 redis-cli 里自查失败计数：
 *   GET    auth:email:fail:someone@qq.com     # 应逐次递增，首次带上 TTL
 *   TTL    auth:email:fail:someone@qq.com     # 应约等于验证码的 300 秒
 *   GET    auth:email:code:someone@qq.com     # 达到 5 次后应为 nil（码已被作废）
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
class EmailCodeServiceImplTest {

    private static final String EMAIL = "someone@qq.com";

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailCodeServiceImpl emailCodeService;

    /** 把 Lua 的返回值固定住，考查 Java 侧的分支。 */
    private void stubScriptResult(Long scriptResult) {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(scriptResult);
    }

    @Test
    void verify_scriptReturns1_passes() {
        stubScriptResult(1L);

        assertThatCode(() -> emailCodeService.verifyAndConsume(EMAIL, "123456"))
                .doesNotThrowAnyException();
    }

    /**
     * Lua 返回 -2 = 连续失败已达上限、验证码已被作废。
     * 必须报 1011 而不是笼统的 1006 —— 用户得知道「该重新获取了」，
     * 否则会对着一个已经删掉的码一直重试。
     */
    @Test
    void verify_scriptReturnsMinus2_throwsTooManyAttempts() {
        stubScriptResult(-2L);

        assertThatThrownBy(() -> emailCodeService.verifyAndConsume(EMAIL, "000000"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(ErrorCode.EMAIL_CODE_TOO_MANY_ATTEMPTS.getCode());
    }

    /** 码不存在（未发/已过期）与填错，都还是统一的 1006，不给爆破者反馈。 */
    @Test
    void verify_scriptReturnsMinus1_throwsGenericCodeError() {
        stubScriptResult(-1L);

        assertThatThrownBy(() -> emailCodeService.verifyAndConsume(EMAIL, "000000"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(ErrorCode.EMAIL_CODE_ERROR.getCode());
    }

    @Test
    void verify_scriptReturns0_throwsGenericCodeError() {
        stubScriptResult(0L);

        assertThatThrownBy(() -> emailCodeService.verifyAndConsume(EMAIL, "000000"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(ErrorCode.EMAIL_CODE_ERROR.getCode());
    }

    /** 脚本返回 null（Redis 异常等）也不能当成通过。 */
    @Test
    void verify_scriptReturnsNull_throwsGenericCodeError() {
        stubScriptResult(null);

        assertThatThrownBy(() -> emailCodeService.verifyAndConsume(EMAIL, "000000"))
                .isInstanceOf(ServiceException.class);
    }

    /**
     * 校验时两个 key 都要传给 Lua：[1] 验证码、[2] 失败计数。
     * 漏传第二个的话 Lua 里的 INCR 会作用到 nil key 上，计数不生效 —— 防护静默失效。
     */
    @Test
    void verify_passesBothCodeAndFailCounterKeys() {
        stubScriptResult(1L);

        emailCodeService.verifyAndConsume(EMAIL, "123456");

        org.mockito.ArgumentCaptor<List<String>> keysCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(stringRedisTemplate).execute(any(RedisScript.class), keysCaptor.capture(),
                any(), any(), any());
        assertThat(keysCaptor.getValue()).containsExactly(
                AuthConstants.EMAIL_CODE_KEY + EMAIL,
                AuthConstants.EMAIL_CODE_FAIL_KEY + EMAIL);
    }
}
