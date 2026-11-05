package com.aurum.crm.module.crm.model;

import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报价单状态。
 *
 * <p><b>为什么需要它</b>：两边用的词汇量不同 ——
 * <ul>
 *     <li>库里 {@code crm_quotation.status} 是 {@code TINYINT}，5 个码值：
 *       0 草稿 / 1 已发出 / 2 已接受 / 3 已拒绝 / 4 已作废</li>
 *     <li>前端 {@code QUOTATION_STATUS_OPTIONS} 只有 3 个中文标签：
 *       报价中 / 已生成合同 / 已失效（列表的 {@code STATUS_TAG} 配色也按这 3 个匹配）</li>
 * </ul>
 * 所以映射是**多对一归并（读）+ 单对一代表示（写）**：
 *
 * <table border="1">
 *     <caption>双向映射</caption>
 *     <tr><th>枚举</th><th>标签</th><th>读：归并这些码值</th><th>写：落这个码值</th></tr>
 *     <tr><td>{@link #QUOTING}</td><td>报价中</td><td>0 草稿、1 已发出</td><td>1 已发出</td></tr>
 *     <tr><td>{@link #CONTRACTED}</td><td>已生成合同</td><td>2 已接受</td><td>2 已接受</td></tr>
 *     <tr><td>{@link #EXPIRED}</td><td>已失效</td><td>3 已拒绝、4 已作废</td><td>4 已作废</td></tr>
 * </table>
 *
 * <p><b>⚠️ 归并会丢信息，所以更新时必须防漂移</b>：一条 {@code status = 0}（草稿）的报价单，
 * 如果原样 PUT 回去就直接写 {@code 1}，草稿态就被静默升级成「已发出」了；
 * {@code 3}（已拒绝）也会被写成 {@code 4}（已作废）。
 * 所以 Service 在更新时的判断是「请求标签是否等于**当前库值的归并标签**」，
 * 相等就不动 {@code status} 列（见 {@code QuotationServiceImpl#resolveStatusUpdate}）。
 *
 * <p>{@link #getCodes()} 同时被列表的状态筛选胶囊复用
 * （{@code quoting} / {@code contracted} / {@code expired}）。
 *
 * <h3>状态流转（本枚举是唯一出口）</h3>
 *
 * <pre>
 *   新建 ──────────────► 1 已发出（对外「报价中」）
 *   编辑 ──────────────► status 列不动（见上面的防漂移）
 *   转合同 {0,1,2} ────► 2 已接受（对外「已生成合同」）  ← 可重复，一个报价可出多份合同
 *   作废   {0,1} ──────► 4 已作废（对外「已失效」）
 *   已失效 再转合同/作废 ─► 2252（{@code QUOTATION_STATUS_ERROR}）
 * </pre>
 *
 * <p>「哪些库值能转合同 / 能作废」由 {@link #canConvert(Integer)} / {@link #canVoid(Integer)}
 * 统一回答，对应的集合 {@link #convertibleCodes()} / {@link #voidableCodes()} 还被写入侧复用
 * （{@code UPDATE ... WHERE status IN (...) }）—— 校验与更新用同一个集合，
 * 不存在「校验放行了、更新却没匹配上」的窗口。两个集合都从上面的归并关系**派生**，
 * 码值不在这里写第二遍。
 *
 * <p><b>⚠️ 库值 0 与 3 没有任何写入口</b>（新建恒写 1、状态动作只写 2 与 4），
 * 但它们仍会被正确读成「报价中」「已失效」—— 历史数据与脏数据不该因为读不出来而报错，
 * 也不该被顺手改掉。0 被当作「报价中」参与转合同 / 作废，3 是终态。
 */
@Getter
public enum QuotationStatus {

    /** 报价中（库里 0 草稿 / 1 已发出 都归到这里） */
    QUOTING("报价中", 1, 0, 1),

    /** 已生成合同（库里 2 已接受） */
    CONTRACTED("已生成合同", 2, 2),

    /** 已失效（库里 3 已拒绝 / 4 已作废 都归到这里） */
    EXPIRED("已失效", 4, 3, 4);

    /** 写入时使用的代表码值 */
    private final Integer writeCode;

    /** 前端标签（对外唯一口径） */
    private final String label;

    /** 读路径归并进来的所有码值（也用于筛选胶囊的 {@code status IN (...)}） */
    private final Set<Integer> codes;

    QuotationStatus(String label, Integer writeCode, Integer... codes) {
        this.label = label;
        this.writeCode = writeCode;
        this.codes = new LinkedHashSet<>(Arrays.asList(codes));
    }

    /**
     * 码值 → 枚举（**归并**），未知码值返回 {@code null}。
     *
     * <p>未知码值不抛异常：库里若有历史脏码值，一条脏数据不该让整个列表接口 500。
     * 转换器会把 {@code null} 原样透出，前端表格用 {@code dash()} 兜底成 {@code --}。
     */
    public static QuotationStatus ofCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.codes.contains(code))
                .findFirst()
                .orElse(null);
    }

    /** 标签 → 枚举，未知标签返回 {@code null}（不抛异常，由调用方决定怎么报错） */
    public static QuotationStatus ofLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        // 去掉首尾空格 + 忽略大小写：前端下拉给的是精确标签，
        // 但 curl / Knife4j 联调时手敲带尾空格的取值很常见，没必要为此返回 400
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(status -> status.label.toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    /** 码值 → 中文标签（归并后的），未知码值返回 {@code null}（供 MapStruct 的 {@code qualifiedByName} 调用） */
    public static String labelOf(Integer code) {
        QuotationStatus status = ofCode(code);
        return status == null ? null : status.label;
    }

    /**
     * 筛选胶囊取值 → 枚举。
     *
     * <p>只认 {@code quoting} / {@code contracted} / {@code expired} 三个；
     * {@code all} / {@code mine} / {@code subordinate} / {@code starred} 与未知值都返回 {@code null}
     * （由 Service 按各自语义处理 —— 未知值「不筛」而不是「筛空」）。
     */
    public static QuotationStatus ofScope(String scopeKey) {
        if (scopeKey == null || scopeKey.isBlank()) {
            return null;
        }
        // 显式映射而不是 values()[index]：后者依赖枚举声明顺序，
        // 以后往中间插一个新状态就会静默错位
        return switch (scopeKey.trim().toLowerCase(Locale.ROOT)) {
            case "quoting" -> QUOTING;
            case "contracted" -> CONTRACTED;
            case "expired" -> EXPIRED;
            default -> null;
        };
    }

    /** 合法标签的展示串，如 {@code 报价中 / 已生成合同 / 已失效}，用于拼错误提示 */
    public static String labelOptions() {
        return Arrays.stream(values())
                .map(QuotationStatus::getLabel)
                .collect(Collectors.joining(" / "));
    }

    // ==================== 状态流转判定 ====================

    /**
     * 允许转合同的库值 —— 「报价中」（0 草稿 / 1 已发出）∪「已生成合同」（2 已接受），即 {@code {0, 1, 2}}。
     *
     * <p><b>为什么 2 也在里面</b>：一个报价单可以出多份合同
     * （《02-建表文档》「一个商机可出多版报价单」+《状态流转图》§4「允许重复转换」），
     * 所以已生成合同的报价单仍然可以再转一次，每次生成一份新的合同草稿。
     *
     * <p>从两个归并集合派生而不是重写一遍 {@code {0,1,2}}：码值只在枚举上声明一次，
     * 以后调整归并关系时这里自动跟着变。
     *
     * <p>返回不可变集合：它会被直接塞进 SQL 的 {@code IN (...)}，调用方不该改它
     * （{@link #getCodes()} 暴露的仍是可变集合，这是 Lombok {@code @Getter} 的既有形态）。
     */
    public static Set<Integer> convertibleCodes() {
        Set<Integer> codes = new LinkedHashSet<>(QUOTING.codes);
        codes.addAll(CONTRACTED.codes);
        return Collections.unmodifiableSet(codes);
    }

    /** 允许作废的库值 = 「报价中」= {@code {0, 1}}（已生成合同/已失效的单子不能作废） */
    public static Set<Integer> voidableCodes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(QUOTING.codes));
    }

    /**
     * 该库值能否转合同。
     *
     * <p>{@code null} 与未知码值一律返回 {@code false}（fail-closed）：
     * 状态判定的默认答案必须是「不许」，不能让一条脏数据绕过守卫。
     */
    public static boolean canConvert(Integer code) {
        return code != null && convertibleCodes().contains(code);
    }

    /** 该库值能否作废；{@code null} 与未知码值一律 {@code false}（fail-closed，理由同 {@link #canConvert}） */
    public static boolean canVoid(Integer code) {
        return code != null && voidableCodes().contains(code);
    }
}
