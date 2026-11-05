package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 算术验证码响应（接口文档 2.9）。
 *
 * <p>题目以<b>纯文本</b>下发（如 {@code "3 + 5 = ?"}），前端直接显示，
 * 不需要图片、base64 或 {@code <img>}。
 *
 * <p>⚠️ <b>安全局限必须知道</b>：题目是明文给的，会解析文本的脚本可以立刻算出答案并自动提交。
 * 图形验证码至少还要过一道 OCR，而算术题连这道都没有 —— 它只挡得住完全不解析页面的最朴素脚本。
 * 这是换取「用户友好 + 无 AWT 依赖」的代价，不要把它当作和图形码等效的防护。
 */
@Data
@AllArgsConstructor
@Schema(description = "算术验证码")
public class CaptchaRespVO {

    /**
     * 验证码开关是否开启。
     *
     * <p>⚠️ <b>前端必须先看这个字段</b>，不能靠 {@code captchaQuestion} 是不是 null 来判断 ——
     * 全局配了 {@code default-property-inclusion: non_null}，关闭时
     * {@code captchaKey} / {@code captchaQuestion} 会被 JSON <b>整个省略</b>（不是 null），
     * 所以响应长这样：{@code {"enabled":false}}。
     */
    @Schema(description = "验证码开关是否开启；false 时前端应隐藏验证码输入行")
    private Boolean enabled;

    /** 验证码标识，登录时原样带回 {@code POST /auth/login} 的 {@code captchaKey} */
    @Schema(description = "验证码 key，登录时原样带回")
    private String captchaKey;

    /**
     * 题目文本，如 {@code "3 + 5 = ?"}、{@code "42 ÷ 6 = ?"}。
     *
     * <p>直接显示给用户即可，不含任何标记语言。
     */
    @Schema(description = "题目文本，如 \"3 + 5 = ?\"")
    private String captchaQuestion;
}
