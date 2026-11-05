package com.aurum.crm.module.crm.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 单据金额与毛利计算器 —— **《02-建表文档》3.6 的唯一实现**。
 *
 * <p><b>为什么单独抽一个类、而且全是静态纯函数</b>：
 * <ul>
 *     <li>3.6 那节自己标了「必须实现前定稿」，前后端各写一套是必然出事的地方 ——
 *       集中到一处，公式有改动就只改这里</li>
 *     <li>纯函数不依赖 Spring / 数据库，可以直接写单测钉住「先逐行四舍五入再汇总」这条硬规则
 *       （这是最容易在重构中被写成「先汇总再算税」的地方，而它一旦写错，
 *       明细与合计会差几分钱，财务对账时极难定位）</li>
 *     <li>{@code crm_contract_item} / {@code crm_opportunity_item} 与报价单明细结构完全一致
 *       （3.5「三张表共用一个明细行结构」），合同模块可以直接复用本类</li>
 * </ul>
 *
 * <p><b>口径（3.6 原文）</b>，统一约定 {@code unit_price} 为含税单价、{@code amount} 为含税行金额：
 * <pre>
 *   折后单价 = unit_price × (1 − discount_rate / 100)
 *   行金额   = ROUND(quantity × 折后单价, 2)          -- 含税
 *   行不含税 = ROUND(行金额 / (1 + tax_rate / 100), 2)
 *   行税额   = 行金额 − 行不含税

 *   total_with_tax = Σ 行金额
 *   tax_amount     = Σ 行税额
 *   total_amount   = total_with_tax − tax_amount      -- 不含税合计
 * </pre>
 * 「行税额」刻意用两个**已四舍五入**的值相减，保证「不含税 + 税额 === 含税」在分位上恒等。
 *
 * <p><b>毛利（本版新定义的口径）</b>：
 * <pre>
 *   行毛利 = 行不含税 − ROUND(成本价 × quantity, 2)
 *   整单利润 = Σ 行毛利
 * </pre>
 * 等价于 {@code total_amount − Σ(成本价 × 数量)}（因为 {@code total_amount = Σ 行不含税}）。
 * 成本取 {@code crm_product.cost_price}；**任一行成本未知（传 null）→ 整单利润返回 null**，
 * 宁可让前端显示 {@code --}，也不给一个偏高的错数。成本为 {@code 0} 按 0 计
 * （可能真的是零成本，不能把 0 当成「没维护」）。
 */
public final class MoneyCalculator {

    /** 金额小数位：与 DDL 的 {@code DECIMAL(*,2)} 一致 */
    public static final int SCALE = 2;

    private MoneyCalculator() {
    }

    /**
     * 单行的三个金额。
     *
     * @param amount   行金额（含税）
     * @param netAmount 行不含税金额
     * @param taxAmount 行税额（= amount − netAmount）
     */
    public record LineAmounts(BigDecimal amount, BigDecimal netAmount, BigDecimal taxAmount) {
    }

    /**
     * 单据合计。
     *
     * @param totalWithTax 价税合计（Σ 行金额）→ {@code crm_quotation.total_with_tax}
     * @param taxAmount    税额合计（Σ 行税额）→ {@code crm_quotation.tax_amount}
     * @param totalAmount  **不含税**合计 → {@code crm_quotation.total_amount}
     */
    public record DocumentTotals(BigDecimal totalWithTax, BigDecimal taxAmount, BigDecimal totalAmount) {
    }

    /**
     * 计算单行金额。
     *
     * @param quantity     数量（可空，按 0 计）
     * @param unitPrice    含税单价（可空，按 0 计）
     * @param discountRate 折扣率(%)（可空，按 0 计）
     * @param taxRate      税率(%)（可空，按 0 计）
     */
    public static LineAmounts line(BigDecimal quantity, BigDecimal unitPrice,
                                   BigDecimal discountRate, BigDecimal taxRate) {
        BigDecimal discountedPrice = nz(unitPrice).multiply(
                BigDecimal.ONE.subtract(nz(discountRate).movePointLeft(2)));
        BigDecimal amount = round2(nz(quantity).multiply(discountedPrice));
        return split(amount, taxRate);
    }

    /**
     * 已知**含税行金额**，按税率拆出不含税金额与税额。
     *
     * <p><b>读路径用这个方法</b>：库里已经存了 {@code amount}，以存下来的金额为准来回算毛利，
     * 才能保证「整单利润」与「{@code total_amount} 不含税合计」出自同一组数字。
     * 写路径用 {@link #line}（它内部就是算出行金额后调本方法）。
     *
     * @param amount  含税行金额（可空，按 0 计）
     * @param taxRate 税率(%)（可空，按 0 计）
     */
    public static LineAmounts split(BigDecimal amount, BigDecimal taxRate) {
        BigDecimal gross = round2(amount);
        // 除法必须显式给 scale：税率 13% 时 1.13 是有限小数，但 6% 的
        // 「金额 / 1.06」在十进制下不保证终止，不给 scale 会抛 ArithmeticException
        BigDecimal divisor = BigDecimal.ONE.add(nz(taxRate).movePointLeft(2));
        BigDecimal netAmount = divisor.signum() == 0
                ? gross
                : gross.divide(divisor, SCALE, RoundingMode.HALF_UP);

        // 用两个已舍入的值相减，保证「不含税 + 税额 === 含税」在分位上恒等
        return new LineAmounts(gross, netAmount, gross.subtract(netAmount));
    }

    /** 汇总整单：{@code total_amount = total_with_tax − tax_amount}（3.6 原文写法） */
    public static DocumentTotals document(List<LineAmounts> lines) {
        BigDecimal totalWithTax = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        for (LineAmounts line : lines) {
            totalWithTax = totalWithTax.add(line.amount());
            taxAmount = taxAmount.add(line.taxAmount());
        }
        return new DocumentTotals(totalWithTax, taxAmount, totalWithTax.subtract(taxAmount));
    }

    /** 整单利润所需的单行输入 */
    public record ProfitLine(BigDecimal quantity, LineAmounts amounts, BigDecimal costPrice) {
    }

    /**
     * 整单毛利。
     *
     * @param lines 明细行；<b>任一行 {@code costPrice} 为 {@code null} → 返回 {@code null}</b>
     *              （成本未知，宁可不显示）
     * @return 毛利（2 位小数），或 {@code null}
     */
    public static BigDecimal profit(List<ProfitLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (ProfitLine line : lines) {
            if (line.costPrice() == null) {
                return null;
            }
            BigDecimal cost = round2(line.costPrice().multiply(nz(line.quantity())));
            total = total.add(line.amounts().netAmount().subtract(cost));
        }
        return round2(total);
    }

    /** 四舍五入到 2 位小数（{@code HALF_UP}，与前端 {@code utils/number.js#round2} 同一取舍） */
    public static BigDecimal round2(BigDecimal value) {
        return nz(value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 去掉无意义的小数尾零，便于展示（{@code 3.0000 → 3}、{@code 2.5000 → 2.5}、{@code 0.0000 → 0}）。
     *
     * <p>用于「商品总数 = SUM(quantity)」这类既有整数形态也可能带小数的展示值。
     *
     * <p>⚠️ 零要单独判：{@code new BigDecimal("0.0000").stripTrailingZeros()} 在个别 JDK 上
     * 会返回 {@code 0E-4} 这种科学计数形态，序列化成 JSON 是 {@code 0E-4}，前端显示成乱码。
     */
    public static BigDecimal stripZeros(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return value.stripTrailingZeros();
    }

    /** null 安全：null 按 0 计（DDL 上这些都是 {@code NOT NULL DEFAULT 0.00}，null 只可能来自不完整的请求体） */
    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
