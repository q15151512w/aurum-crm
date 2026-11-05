package com.aurum.crm.module.system.service;

/**
 * 邮箱验证码服务：发送到邮箱、校验并消费。
 *
 * <p><b>不区分场景</b>：登录 / 注册 / 找回密码共用同一套验证码，
 * 与之前的短信实现一致 —— 区分场景要多维护三套模板与三个 key，而文案其实一样。
 *
 * <p><b>与旧短信实现的区别</b>：短信那版因为没接服务商，只把验证码写进 Redis 再打日志；
 * 这一版会**真正通过 SMTP 发信**，所以多了「发信失败」这条分支
 * （见 {@code ErrorCode.EMAIL_SEND_FAILED}）。
 */
public interface EmailCodeService {

    /**
     * 发送验证码到指定邮箱。
     *
     * <p>同一邮箱 60 秒内只能发一次，超了抛 {@code 1004}。
     * 邮件发送失败抛 {@code 1009}（此时**不会**把用户锁在 60 秒限流里，见实现类说明）。
     *
     * @param email 目标邮箱，大小写不敏感
     */
    void sendCode(String email);

    /**
     * 校验并消费验证码。
     *
     * <p>校验通过后立即失效（一次性）。失败统一抛 {@code 1006}，
     * <b>不区分</b>「没发过 / 过期 / 填错」—— 避免给爆破者提供反馈。
     *
     * @param email 发送时用的邮箱（大小写不敏感）
     * @param code  用户填的 6 位验证码
     */
    void verifyAndConsume(String email, String code);
}
