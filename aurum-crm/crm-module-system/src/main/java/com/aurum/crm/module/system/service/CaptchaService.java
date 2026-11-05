package com.aurum.crm.module.system.service;

import com.aurum.crm.module.system.model.vo.CaptchaRespVO;

/**
 * 算术验证码服务（接口文档 2.9）。
 *
 * <p>随机出一道四则运算题（加减乘除，运算数 1~10），用户填计算结果。
 *
 * <p>只服务于<b>密码登录</b>，用于挡住针对 {@code POST /auth/login} 的密码爆破。
 * 邮箱验证码登录、注册、忘记密码都不走它。
 *
 * <p><b>⚠️ 安全局限</b>：题目明文下发，会解析文本的脚本能直接算出答案。
 * 它挡得住的是「不解析页面、只会盲目重放」的最朴素脚本，不等于图形验证码的强度。
 */
public interface CaptchaService {

    /**
     * 生成一道验证码题目。
     *
     * <p>开关关闭时返回 {@code enabled = false} 且不带题目，前端据此隐藏整个输入行。
     */
    CaptchaRespVO generate();

    /**
     * 校验并消费验证码。
     *
     * <p><b>一道题只能答一次</b>：无论答对答错，key 都会被删除。
     * 算术题答案空间小，这条比在图形码上更关键。
     *
     * <p>开关关闭时直接放行（不校验）。
     *
     * @throws com.aurum.crm.common.exception.ServiceException 验证码错误或已过期（1008）
     */
    void verifyAndConsume(String captchaKey, String captchaCode);
}
