package com.aurum.crm.module.crm.model;

import lombok.Getter;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 商机阶段。
 *
 * <p><b>为什么需要这个枚举</b>：阶段在两边用了两种表示 ——
 * <ul>
 *     <li>数据库 {@code crm_opportunity.stage} 是 {@code TINYINT}（1 初步接洽 … 6 输单）；
 *       种子数据、{@code idx_stage} 索引、漏斗的 {@code GROUP BY} 都用码值</li>
 *     <li>{@code crm-ui} 前端一律用**中文标签**：{@code OPPORTUNITY_STAGE_OPTIONS}
 *       的 {@code label} 与 {@code value} 都是中文，列表的 {@code STAGE_TAG} 配色、
 *       详情页的阶段标签、变更阶段的下拉全部按中文匹配</li>
 * </ul>
 * 转换只发生在 {@link com.aurum.crm.module.crm.model.convert.OpportunityConvert}
 * 与 Service 两处，接口的对外口径统一是**中文标签**（与产品模块「对外字段名跟前端走」同一套做法）。
 *
 * <p><b>本枚举只管阶段，不管概率/折扣</b>：{@code crm_opportunity.win_rate}（赢单概率）
 * 已被改名成 {@code discount_rate}（整单折扣），折扣与阶段没有任何关系，
 * 所以早期版本这里那个「每个阶段的默认赢单概率」（{@code defaultWinRate}）已经删除。
 */
@Getter
public enum OpportunityStage {

    /** 初步接洽 */
    INITIAL_CONTACT(1, "初步接洽"),

    /** 需求确认 */
    NEED_CONFIRMED(2, "需求确认"),

    /** 方案报价 */
    SOLUTION_QUOTED(3, "方案报价"),

    /** 商务谈判 */
    NEGOTIATING(4, "商务谈判"),

    /** 赢单（终态） */
    WON(5, "赢单"),

    /** 输单（终态） */
    LOST(6, "输单");

    /** 默认阶段：前端 {@code emptyForm()} 的 {@code stage} 就是「初步接洽」 */
    public static final OpportunityStage DEFAULT = INITIAL_CONTACT;

    private final Integer code;
    private final String label;

    OpportunityStage(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 码值 → 枚举，未知码值返回 {@code null}（不抛异常）。
     *
     * <p>读路径上不能抛：库里若有历史脏码值，一条脏数据不该让整个列表接口 500。
     * 转换器会把 {@code null} 原样透出，前端表格用 {@code dash()} 兜底成 {@code --}。
     */
    public static OpportunityStage ofCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(stage -> stage.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 中文标签 → 枚举，未知标签返回 {@code null}（不抛异常，由调用方决定怎么报错）。
     *
     * <p>刻意做「去空格 + 大小写无关」的宽松匹配：前端下拉给的就是精确标签，
     * 但 curl / Knife4j 联调时手敲 {@code "赢单 "} 这种带尾空格的情况很常见，
     * 没必要为此返回一个 400。
     */
    public static OpportunityStage ofLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(stage -> stage.label.toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    /** 码值 → 中文标签，未知码值返回 {@code null}（供 MapStruct 的 {@code qualifiedByName} 调用） */
    public static String labelOf(Integer code) {
        OpportunityStage stage = ofCode(code);
        return stage == null ? null : stage.label;
    }

    /**
     * 合法标签的展示串，如 {@code 初步接洽 / 需求确认 / …}。
     *
     * <p>只用于拼错误提示 —— 用户填错时告诉他「能填什么」，
     * 比只回一句「阶段不合法」有用得多。
     */
    public static String labelOptions() {
        return Arrays.stream(values())
                .map(OpportunityStage::getLabel)
                .collect(Collectors.joining(" / "));
    }
}
