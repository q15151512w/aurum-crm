package com.aurum.crm.module.system.service.impl;

import cn.hutool.core.util.IdUtil;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.system.core.AuthConstants;
import com.aurum.crm.module.system.model.vo.CaptchaRespVO;
import com.aurum.crm.module.system.service.CaptchaService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 算术验证码实现：随机出一道四则运算题，答案存 Redis。
 *
 * <p>题目是纯文本，不生成图片 —— 所以没有 AWT 依赖，也不消耗画图的 CPU。
 *
 * <p><b>⚠️ 安全局限</b>：题目明文下发，会解析文本的脚本能直接算出答案。
 * 它只挡得住完全不解析页面的最朴素脚本。要更强的防护得回到图形验证码（需过 OCR）。
 * 当前是有意识地选了「用户友好」这一侧。
 */
@Slf4j
@Service
public class CaptchaServiceImpl implements CaptchaService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 验证码开关。
     *
     * <p>{@code :true} 这个默认值不能省 —— 省了的话配置文件里没有这一项时启动直接报错，
     * 而不是按预期走「默认开启」。
     */
    @Value("${aurum.auth.captcha.enabled:true}")
    private boolean enabled;

    /**
     * 校验并消费验证码的 Lua 脚本。
     *
     * <p><b>先 DEL 再比对</b>，即一张题只能答一次。算术题的答案空间很小
     * （加减乘除的结果大致落在 0~100），若能对同一道题反复试，几乎等于没有防护，
     * 所以一次性消费在这里比在图形码上更重要。
     *
     * <p>返回值：{@code 1} 通过；{@code -1} key 不存在或已过期；{@code 0} 不匹配（key 已删）。
     */
    private static final DefaultRedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>("""
            local v = redis.call('GET', KEYS[1])
            if v == false then return -1 end
            redis.call('DEL', KEYS[1])
            if v ~= ARGV[1] then return 0 end
            return 1
            """, Long.class);

    /** 一道题：题面 + 答案 */
    private record Question(String text, int answer) {
    }

    @Override
    public CaptchaRespVO generate() {
        if (!enabled) {
            // key 与题目传 null：全局的 non_null 会把它们从 JSON 里省略，
            // 前端只看到 {"enabled":false}，从而隐藏整个验证码行
            return new CaptchaRespVO(false, null, null);
        }

        Question question = randomQuestion();
        String captchaKey = IdUtil.fastSimpleUUID();

        stringRedisTemplate.opsForValue().set(
                AuthConstants.CAPTCHA_KEY + captchaKey,
                String.valueOf(question.answer()),
                AuthConstants.CAPTCHA_TTL);

        return new CaptchaRespVO(true, captchaKey, question.text());
    }

    @Override
    public void verifyAndConsume(String captchaKey, String captchaCode) {
        if (!enabled) {
            return;
        }

        // 「必填」取决于运行时开关，所以校验放这里而不是 LoginReqVO 的 @NotBlank ——
        // 加了注解的话，开关关闭时前端不传这两个字段会被参数校验拦成 400
        if (!StringUtils.hasText(captchaKey) || !StringUtils.hasText(captchaCode)) {
            throw new ServiceException(ErrorCode.CAPTCHA_CODE_ERROR);
        }

        Long result = stringRedisTemplate.execute(
                VERIFY_SCRIPT,
                Collections.singletonList(AuthConstants.CAPTCHA_KEY + captchaKey),
                captchaCode.trim());

        if (result == null || result != 1L) {
            // 不区分「过期」和「算错」：给爆破者更少的反馈
            log.warn("[算术验证码校验失败] key={} result={}", captchaKey, result);
            throw new ServiceException(ErrorCode.CAPTCHA_CODE_ERROR);
        }
    }

    /**
     * 随机出一道四则运算题。
     *
     * <p><b>取值约束</b>：加减乘除的<b>两个运算数</b>都取 {@code [CAPTCHA_MIN, CAPTCHA_MAX]}
     * （当前 0~9）。除法的<b>被除数允许超出这个范围</b>（最大 9×9=81），
     * 因为它是两个因数相乘的产物，不是用户要填的那个数 —— 见
     * {@link #randomDivision()} 的说明。
     *
     * <p>三条约束保证用户一定填得出来：
     * <ul>
     *     <li><b>除法只出能整除的</b>：题面写 {@code (a×d) ÷ d}，答案就是 a。
     *         直接随机 {@code a ÷ b} 的话绝大多数除不尽（如 7÷3），用户没法填整数</li>
     *     <li><b>除数不为 0</b>：运算数范围含 0，所以除法的除数要单独保证 ≥ 1</li>
     *     <li><b>减法不出负数</b>：保证被减数 ≥ 减数，答案总在 0 及以上，用户不用输负号</li>
     * </ul>
     *
     * <p>答案可能是 0（如 {@code 5 - 5 = ?}、{@code 0 + 0 = ?}），前端的校验规则要允许它。
     */
    private Question randomQuestion() {
        int a = randomOperand();
        int b = randomOperand();

        return switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> new Question(a + " + " + b + " = ?", a + b);
            case 1 -> {
                // 保证被减数不小于减数，不出负数
                int big = Math.max(a, b);
                int small = Math.min(a, b);
                yield new Question(big + " - " + small + " = ?", big - small);
            }
            case 2 -> new Question(a + " × " + b + " = ?", a * b);
            default -> randomDivision();
        };
    }

    /** {@code [CAPTCHA_MIN, CAPTCHA_MAX]} 闭区间内的随机运算数 */
    private int randomOperand() {
        return ThreadLocalRandom.current().nextInt(AuthConstants.CAPTCHA_MIN, AuthConstants.CAPTCHA_MAX + 1);
    }

    /**
     * 出一道能整除的除法题：{@code (factor × divisor) ÷ divisor = factor}。
     *
     * <p>两个因数（乘数 factor 与除数 divisor）都在 {@code [CAPTCHA_MIN, CAPTCHA_MAX]} 内，
     * 答案就是 factor，也在这个范围内；<b>被除数 = factor × divisor 会超出范围</b>
     * （最大 9×9=81），这是有意接受的 —— 被除数是两个因数相乘的产物，
     * 用户不需要填它，题面显示 {@code 72 ÷ 9 = ?} 是正常的。
     *
     * <p>⚠️ 不要「顺手」把被除数也压到 {@code CAPTCHA_MAX} 以内 —— 那样能让整除成立的
     * 组合会骤减到只剩 {@code 6÷3}、{@code 8÷4} 等寥寥几种，题目会高度重复。
     *
     * <p>除数的下界抬到 1（而不是直接用 {@code CAPTCHA_MIN}）是因为除数不能为 0。
     */
    private Question randomDivision() {
        int factor = randomOperand();
        int divisor = ThreadLocalRandom.current().nextInt(
                Math.max(1, AuthConstants.CAPTCHA_MIN), AuthConstants.CAPTCHA_MAX + 1);

        return new Question((factor * divisor) + " ÷ " + divisor + " = ?", factor);
    }
}
