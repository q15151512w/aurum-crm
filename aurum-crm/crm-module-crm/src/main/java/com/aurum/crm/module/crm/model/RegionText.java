package com.aurum.crm.module.crm.model;

/**
 * 省市区拼接串的生成。
 *
 * <h3>为什么需要它</h3>
 *
 * <p>库里存的是 {@code province} / {@code city} / {@code district} **三个独立列**
 * （客户与线索表都是如此，没有 {@code region} 列），而前端的地区控件对外是一个
 * 拼接串（{@code '广东省广州市天河区'}）—— 列表要显示它，编辑弹窗也要靠它回填级联选择器。
 *
 * <p>于是响应里额外给一个派生字段 {@code region}，由 Converter 的 {@code @AfterMapping}
 * 调这里生成。这样前端在「展示」和「回填」两个方向上都不用自己拼，
 * 与 {@code id} / {@code name} / {@code finalAmount} 这类别名是同一套做法
 * （「对外字段名跟前端走」，见《05-接口文档》的字段口径约定）。
 *
 * <p><b>入参为 null 的段直接跳过</b>：省市可能只填了一级，拼出来是
 * {@code '山东省'} 而不是 {@code '山东省null'}。三段全空时返回 {@code null}
 * （而不是空串），让 Jackson 的 {@code non_null} 策略把它整个省略，
 * 前端 {@code dash()} 自然兜底成 {@code --}。
 */
public final class RegionText {

    /**
     * 拼成 {@code '广东省广州市天河区'}。
     *
     * @return 三段全空时返回 {@code null}
     */
    public static String join(String province, String city, String district) {
        StringBuilder sb = new StringBuilder();
        append(sb, province);
        append(sb, city);
        append(sb, district);
        return sb.isEmpty() ? null : sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            sb.append(part.trim());
        }
    }

    private RegionText() {
    }
}
