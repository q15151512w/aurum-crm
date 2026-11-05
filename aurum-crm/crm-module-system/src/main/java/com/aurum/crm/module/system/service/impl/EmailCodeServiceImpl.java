package com.aurum.crm.module.system.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.system.core.AuthConstants;
import com.aurum.crm.module.system.service.EmailCodeService;
import jakarta.annotation.Resource;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 邮箱验证码实现：Redis 存储 + SMTP 发信。
 *
 * <p><b>为什么用 {@link StringRedisTemplate} 而不是 {@code RedisTemplate}</b>：
 * value 就是一个 6 位数字字符串，没有对象序列化需求。
 * {@code redis-cli} 里 {@code GET auth:email:code:a@b.com} 直接可读，排障成本最低；
 * 也避开了自定义 {@code RedisTemplate} 与 Sa-Token 的 {@code sa-token-redis-jackson}
 * 之间序列化器互相干扰的风险。（与旧的短信实现同一个理由。）
 *
 * <p><b>三个必须守住的点</b>：
 * <ol>
 *     <li><b>邮箱归一化</b> —— {@code A@x.com} 与 {@code a@x.com} 是同一个邮箱，
 *         不归一化会落成两个 Redis key，用户换个大小写就能绕过 60 秒限流。</li>
 *     <li><b>发信失败要退还限流</b> —— 否则用户收不到邮件，还被锁 60 秒不能重发。</li>
 *     <li><b>不把验证码写进日志</b> —— 短信那版因为没接服务商才打日志，
 *         现在真的发信了，日志里有码等于「能看日志的人可以登录任意账号」。</li>
 * </ol>
 */
@Slf4j
@Service
public class EmailCodeServiceImpl implements EmailCodeService {

    /** 验证码位数 */
    private static final int CODE_LENGTH = 6;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private JavaMailSender mailSender;

    /** 发件人地址。163 要求发件人必须与登录账号一致，所以默认取 spring.mail.username */
    @Value("${spring.mail.username:}")
    private String from;

    /**
     * 校验并消费验证码的 Lua 脚本。
     *
     * <p><b>为什么必须用脚本</b>：{@code GET} 和 {@code DEL} 是两条命令，
     * 并发时两个请求可能同时 GET 命中，各自登录成功一次 —— 同一个验证码被用了两次。
     * 放进一段 Lua 由 Redis 单线程原子执行，才能杜绝这种「双花」。
     * 失败计数也必须在同一个脚本里自增，否则「读计数 → 判断 → 写计数」之间又有窗口。
     *
     * <p><b>KEYS</b>：[1] 验证码 {@code auth:email:code:{email}}，[2] 失败计数 {@code auth:email:fail:{email}}
     * <br><b>ARGV</b>：[1] 用户提交的验证码，[2] 失败计数 TTL（秒），[3] 允许的最大失败次数
     *
     * <p>返回值：
     * <ul>
     *     <li>{@code 1} 校验通过，验证码与失败计数都已删除</li>
     *     <li>{@code -1} 验证码不存在或已过期（不计数 —— 没有码可爆）</li>
     *     <li>{@code 0} 不匹配，已记一次失败，但还没到上限</li>
     *     <li>{@code -2} 不匹配，且这次已达上限 —— 验证码已被删除，必须重新获取</li>
     * </ul>
     *
     * <p>成功路径的顺序是「先比对、后删除」，即验证通过才失效。
     *
     * <p>⚠️ <b>为什么失败要计数</b>：6 位码只有 100 万种，而下面 60 秒的发送限流是
     * 「按邮箱」的 —— 攻击者可以一直重发，把这个验证码的有效期无限续期，
     * 从而对同一个 6 位数无限次猜测。没有这道闸门，邮箱验证码登录等于没有防护。
     */
    private static final DefaultRedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>("""
            local v = redis.call('GET', KEYS[1])
            if v == false then
                -- 码没了：可能是过期，也可能是刚刚错满次数被上面删掉了。
                -- 靠失败计数区分，好让「次数用尽」这个提示能稳定重复出现 ——
                -- 否则用户第 6 次提交突然变成「验证码错误或已过期」，会以为是码过期而去干等。
                local fails = tonumber(redis.call('GET', KEYS[2]) or '0')
                if fails >= tonumber(ARGV[3]) then return -2 end
                return -1
            end
            if v ~= ARGV[1] then
                local fails = redis.call('INCR', KEYS[2])
                if fails == 1 then
                    redis.call('EXPIRE', KEYS[2], ARGV[2])
                end
                if fails >= tonumber(ARGV[3]) then
                    redis.call('DEL', KEYS[1])
                    return -2
                end
                return 0
            end
            redis.call('DEL', KEYS[1])
            redis.call('DEL', KEYS[2])
            return 1
            """, Long.class);

    @Override
    public void sendCode(String email) {
        String to = normalize(email);

        // 1) 限流：SET NX EX 是单条原子命令，天然解决并发重复发送。
        //    ⚠️ 不要写成 hasKey + set —— 两步之间有窗口，并发能穿过去。
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(AuthConstants.EMAIL_LIMIT_KEY + to, "1", AuthConstants.EMAIL_LIMIT_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new ServiceException(ErrorCode.CODE_SEND_TOO_FREQUENT);
        }

        // 2) 生成验证码并落 Redis。限流 key 60 秒过期、验证码 key 5 分钟过期，两者互不影响；
        //    60 秒后重发会覆盖验证码，老码自然失效。
        String code = RandomUtil.randomNumbers(CODE_LENGTH);
        stringRedisTemplate.opsForValue()
                .set(AuthConstants.EMAIL_CODE_KEY + to, code, AuthConstants.EMAIL_CODE_TTL);
        // 顺带清掉上一个验证码攒下的失败计数。
        // ⚠️ 不清的话，上一轮错满 5 次的用户重发之后仍然处于「一提交就报次数过多」的状态 ——
        //    新码永远用不了，只能等 5 分钟计数自己过期。这是纯粹的坑人。
        stringRedisTemplate.delete(AuthConstants.EMAIL_CODE_FAIL_KEY + to);

        // 3) 真正发信。
        try {
            sendMail(to, code);
        } catch (Exception e) {
            // ⚠️ 关键：发信失败必须【退还限流】。
            //    否则用户既没收到邮件、又被锁 60 秒不能重发 —— 纯粹白等，
            //    而且看到的是「发送过于频繁」，会以为是自己在狂点，查不到真因。
            stringRedisTemplate.delete(AuthConstants.EMAIL_LIMIT_KEY + to);
            // 验证码本身也一起删掉：那封信既然没发出去，这个码就不该能被用
            stringRedisTemplate.delete(AuthConstants.EMAIL_CODE_KEY + to);

            // ⚠️ 认证失败要单独处理：它是**配置问题**，不是「稍后重试」能解决的。
            //    163 在「授权码错」和「SMTP 服务没开」两种情况下都只回 535，
            //    这条坑本项目已经卡过两轮（见 application-dev.yml 的注释），
            //    所以这里把「该去查哪四处」直接写进日志 —— 排查成本从「一轮」降到「一眼」。
            if (isAuthFailure(e)) {
                log.error("""
                        [邮箱验证码] SMTP 认证失败（163 返回 535）—— 这是【配置问题】，重试不会成功。请依次检查：
                          ① 163 网页版 → 设置 → POP3/SMTP/IMAP → 「SMTP服务」是否为【开启】；
                          ② spring.mail.password 填的是否是【授权码】（不是邮箱登录密码）；
                          ③ 授权码若重置过，旧码会【立即失效】，必须填新的（且只显示一次）；
                          ④ spring.mail.username 是否为完整邮箱地址（如 admin@example.com）。
                        另：163 的「服务没开」与「授权码错」返回的是同一个 535，光看这个码区分不了，
                        要确认开关状态只能去网页版看。排查步骤详见 README「常见问题」。
                        email={} 原因={}""",
                        maskEmail(to), e.getMessage());
                throw new ServiceException(ErrorCode.EMAIL_SERVICE_AUTH_FAILED);
            }

            log.error("[邮箱验证码] 发送失败 email={} 原因={}", maskEmail(to), e.getMessage(), e);
            throw new ServiceException(ErrorCode.EMAIL_SEND_FAILED);
        }

        // ⚠️ 只记「已发送」，不记验证码本身
        log.info("[邮箱验证码] 已发送 email={} ttl={}s", maskEmail(to),
                AuthConstants.EMAIL_CODE_TTL.toSeconds());
    }

    @Override
    public void verifyAndConsume(String email, String code) {
        String to = normalize(email);
        Long result = stringRedisTemplate.execute(VERIFY_SCRIPT,
                List.of(AuthConstants.EMAIL_CODE_KEY + to, AuthConstants.EMAIL_CODE_FAIL_KEY + to),
                code,
                String.valueOf(AuthConstants.EMAIL_CODE_TTL.toSeconds()),
                String.valueOf(AuthConstants.EMAIL_CODE_MAX_ATTEMPTS));

        if (result != null && result == -2L) {
            // 连续错太多次，这个码已经作废。必须明确告诉用户去重新获取 ——
            // 否则他会对着一个已经删掉的码一直重试，永远进不去。
            log.warn("[邮箱验证码] 连续失败达上限，验证码已作废 email={} max={}",
                    maskEmail(to), AuthConstants.EMAIL_CODE_MAX_ATTEMPTS);
            throw new ServiceException(ErrorCode.EMAIL_CODE_TOO_MANY_ATTEMPTS);
        }
        if (result == null || result != 1L) {
            // 不区分「过期」和「填错」，避免给爆破者提供反馈
            log.warn("[邮箱验证码] 校验失败 email={} result={}", maskEmail(to), result);
            throw new ServiceException(ErrorCode.EMAIL_CODE_ERROR);
        }
    }

    // ========================================================================
    //  内部
    // ========================================================================

    /**
     * 判断是不是「SMTP 认证失败」（163 的那个 535）。
     *
     * <p>Spring 把底层的 {@code jakarta.mail.AuthenticationFailedException} 包成了
     * {@link MailAuthenticationException}，但中间还可能套着别的异常，
     * 所以沿 {@code cause} 链一路找；同时兜一个「消息里含 535」的判断 ——
     * {@code 535} 是 SMTP 判定认证失败的固定码，比异常类型更直接、也更耐重构。
     *
     * <p>⚠️ 这里**只**用来区分「配置问题」与「可重试的发送失败」，
     * 不做任何自动修复：授权码和服务开关都在 163 那边，代码无能为力。
     */
    private static boolean isAuthFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof MailAuthenticationException) {
                return true;
            }
            if (t.getMessage() != null && t.getMessage().contains("535")) {
                return true;
            }
            if (t.getCause() == t) {
                break; // 防御自引用的异常链，避免死循环
            }
        }
        return false;
    }

    private void sendMail(String to, String code) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        // false = 不需要 multipart（没有附件）；编码用 UTF-8，否则中文主题会乱码
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject("【AurumCRM】邮箱验证码");
        helper.setText(buildBody(code), true);
        mailSender.send(message);
    }

    /**
     * 邮件正文（HTML）。
     *
     * <p>纯内联样式，不用外部 CSS —— 邮件客户端（尤其 QQ 邮箱、Outlook）对外部样式表支持很差。
     * 验证码特意做得大而醒目，用户从锁屏通知里瞄一眼就能抄。
     */
    private String buildBody(String code) {
        long minutes = AuthConstants.EMAIL_CODE_TTL.toMinutes();
        return """
                <div style="font-family:-apple-system,'Segoe UI',Roboto,'Microsoft YaHei',sans-serif;
                            max-width:480px;margin:0 auto;padding:24px;color:#1f2329;">
                  <h2 style="margin:0 0 8px;font-size:18px;">AurumCRM · 邮箱验证码</h2>
                  <p style="margin:0 0 20px;color:#646a73;font-size:14px;">
                    你正在使用本邮箱进行身份验证，验证码是：
                  </p>
                  <div style="background:#f2f3f5;border-radius:8px;padding:20px;text-align:center;
                              font-size:32px;font-weight:700;letter-spacing:8px;color:#2c6ddb;">
                    %s
                  </div>
                  <p style="margin:20px 0 0;color:#646a73;font-size:13px;line-height:1.7;">
                    验证码 <b>%d 分钟内有效</b>，且<b>只能使用一次</b>。<br>
                    如果不是你本人操作，忽略本邮件即可，你的账号不会有任何变化。
                  </p>
                </div>
                """.formatted(code, minutes);
    }

    /**
     * 邮箱归一化：去空格 + 转小写。
     *
     * <p>邮箱本身大小写不敏感（域名部分尤其），而 Redis key 是大小写敏感的字符串 ——
     * 不统一就会「同一邮箱两个 key」，用户靠换个大小写就能绕过限流、或者
     * 用大写收码却用小写验证导致「验证码错误」。
     */
    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 日志里对邮箱打码：{@code zhangsan@qq.com} → {@code zh***@qq.com}。
     *
     * <p>邮箱是个人信息，日志里没必要存全量；出问题时看域名 + 首字母足以定位。
     */
    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String name = email.substring(0, at);
        String domain = email.substring(at);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.substring(0, 2) + "***" + domain;
    }
}
