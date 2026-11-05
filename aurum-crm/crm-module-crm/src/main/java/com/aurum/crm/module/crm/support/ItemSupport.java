package com.aurum.crm.module.crm.support;

import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.mapper.ProductMiniMapper;
import com.aurum.crm.module.crm.mapper.SysUnitMiniMapper;
import com.aurum.crm.module.crm.model.DocumentItem;
import com.aurum.crm.module.crm.model.ProductMiniDO;
import com.aurum.crm.module.crm.model.SysUnitMiniDO;
import com.aurum.crm.module.crm.model.dto.DocumentItemSaveReqVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 单据明细行的共用机制 —— 报价单 / 商机（以及将来的合同）三张明细表共用这一套。
 *
 * <p><b>为什么抽出来</b>：《02-建表文档》3.5 明确「三张表共用一个明细行结构」，
 * 而明细要做的事完全一样（默认值兜底 → 产品分类快照 → 计量单位解析 → 行金额重算 → 排序）。
 * 不抽的话就是三份一模一样的代码，将来改金额口径必然漏改一处 —— 那是会算错钱的地方。
 *
 * <p><b>DO 各写各的</b>：{@code @TableName} 只能标在具体类上，所以每张表仍有自己的 DO；
 * 这里通过 {@link DocumentItem} 接口操作它们，创建的实例由调用方用 {@code Supplier} 传进来
 * （如 {@code OpportunityItemDO::new}）。
 *
 * <p><b>两处刻意「宽松」的处理</b>（与产品模块的严格 400 不同，理由都写在方法注释里）：
 * <ul>
 *     <li>{@code unitName} 解析不到 → 落 null + warn，不挡住保存</li>
 *     <li>{@code productId} 查不到 → 按「手填行」处理，不报 400</li>
 * </ul>
 */
@Slf4j
@Component
public class ItemSupport {

    /**
     * 单据明细行数上限。
     *
     * <p>DDL 没有这个限制，这里挡的是超预期的 payload（前端一次最多也就几行到几十行）。
     */
    public static final int MAX_ITEMS = 200;

    @Resource
    private ProductMiniMapper productMiniMapper;
    @Resource
    private SysUnitMiniMapper sysUnitMiniMapper;

    /**
     * 批量取产品（分类快照 / 兜底单位 / 成本价）—— 一次查询，不做 N+1。
     *
     * <p>走 {@link ProductMiniMapper}（{@code ProductMiniDO} 不含 {@code delFlag}）→
     * **不过滤逻辑删除**：产品下架/删除不代表历史单据当初的明细不存在，
     * 报价单还要靠它算毛利（见 {@code docs/11-报价单模块字段契约.md}）。
     */
    public Map<Long, ProductMiniDO> loadProducts(Collection<Long> productIds) {
        List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            // ⚠️ 必须用 Collections.emptyMap() 而不是 Map.of()：Map.of() 的 get(null) 会抛 NPE，
            //    而调用方一定会用 productId（可能为 null）去取
            return Collections.emptyMap();
        }
        return productMiniMapper.selectList(ProductMiniDO::getProductId, ids).stream()
                .collect(Collectors.toMap(ProductMiniDO::getProductId, product -> product, (a, b) -> a));
    }

    /** 批量取计量单位名（明细回显用 —— 前端编辑器的单位下拉靠它回填） */
    public Map<Long, String> loadUnitNames(Collection<Long> unitIds) {
        List<Long> ids = unitIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysUnitMiniMapper.selectList(SysUnitMiniDO::getUnitId, ids).stream()
                .filter(unit -> unit.getUnitName() != null)
                .collect(Collectors.toMap(SysUnitMiniDO::getUnitId, SysUnitMiniDO::getUnitName, (a, b) -> a));
    }

    /** 行数上限校验（{@code POST} 传「已有 + 新增」的总数） */
    public void validateCount(int totalRows, String docLabel) {
        if (totalRows > MAX_ITEMS) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    docLabel + "的产品明细不能超过 " + MAX_ITEMS + " 行，当前 " + totalRows + " 行");
        }
    }

    /**
     * 请求行 → 真正要落库的 DO 列表。
     *
     * <p>每一步都是 3.6 与「快照原则」要求的：
     * <ol>
     *     <li>默认值兜底成 DDL 的 {@code NOT NULL DEFAULT}（数量 1 / 单价 0 / 折扣 0 / 税率 0）</li>
     *     <li>{@code productTypeId} ← 产品的 {@code category_id}（分类快照）</li>
     *     <li>{@code unitId} ← 显式 unitId → 按单位名查 {@code sys_unit} → 产品的单位 → null</li>
     *     <li>{@code amount} = {@code MoneyCalculator.line(...)}，**覆盖客户端提交的值**（只记 debug 日志）</li>
     *     <li>{@code sort} 从 {@code startSort} 起按提交顺序递增（{@code PUT} 传 1、{@code POST} 传现有最大 sort+1）</li>
     * </ol>
     *
     * <p><b>不设置</b>主键、外键、审计字段 —— 由各自的 Service 负责。
     *
     * @param rows      请求里的明细行（已通过 {@code @Valid} 校验）
     * @param startSort 首行的排序值
     * @param factory   目标 DO 的构造器，如 {@code OpportunityItemDO::new}
     * @param docLabel  单据中文名，只用于日志与报错文案（「报价单」/「商机」）
     */
    public <T extends DocumentItem> List<T> buildItems(List<DocumentItemSaveReqVO> rows,
                                                       int startSort,
                                                       Supplier<T> factory,
                                                       String docLabel) {
        List<DocumentItemSaveReqVO> safeRows = rows == null ? List.of() : rows;
        if (safeRows.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, ProductMiniDO> products = loadProducts(safeRows.stream()
                .map(DocumentItemSaveReqVO::getProductId).toList());
        Map<String, Long> unitIdByName = loadUnitIdByName(safeRows);
        Set<Long> explicitUnitIds = loadExplicitUnitIds(safeRows);

        List<T> items = new ArrayList<>(safeRows.size());
        int sort = startSort;
        for (DocumentItemSaveReqVO row : safeRows) {
            // 产品查不到（或没传 productId）→ 按「手填行」处理：没有分类快照，单位只能来自 unitName。
            // 刻意不报 400：前端允许手填一个不在产品目录里的名称，明细的 name/price 本来就是快照。
            ProductMiniDO product = row.getProductId() == null ? null : products.get(row.getProductId());

            T item = factory.get();
            item.setProductId(row.getProductId());
            item.setProductName(row.getProductName());
            item.setProductTypeId(product == null ? null : product.getCategoryId());
            item.setUnitId(resolveUnitId(row, product, unitIdByName, explicitUnitIds));
            item.setQuantity(row.getQuantity() == null ? BigDecimal.ONE : row.getQuantity());
            item.setUnitPrice(row.getUnitPrice() == null ? BigDecimal.ZERO : row.getUnitPrice());
            item.setDiscountRate(row.getDiscountRate() == null ? BigDecimal.ZERO : row.getDiscountRate());
            item.setTaxRate(row.getTaxRate() == null ? BigDecimal.ZERO : row.getTaxRate());
            item.setRemark(row.getRemark());
            item.setSort(sort++);

            // 行金额以服务端重算为准（3.6：先逐行四舍五入），客户端值只用来发现口径漂移
            MoneyCalculator.LineAmounts amounts = MoneyCalculator.line(
                    item.getQuantity(), item.getUnitPrice(), item.getDiscountRate(), item.getTaxRate());
            item.setAmount(amounts.amount());
            if (row.getAmount() != null && row.getAmount().compareTo(amounts.amount()) != 0) {
                log.debug("[{}] 明细「{}」客户端行金额与 3.6 重算不一致，已按服务端值落库：client={}, server={}",
                        docLabel, item.getProductName(), row.getAmount(), amounts.amount());
            }
            items.add(item);
        }
        return items;
    }

    /**
     * 解析明细行的 {@code unit_id}。
     *
     * <p>优先级：显式 {@code unitId}（且真实存在）→ 按 {@code unitName} 查 {@code sys_unit}
     * → 产品的单位 → null。
     *
     * <p><b>⚠️ 与产品模块刻意不同：这里是「宽松」的</b>。产品模块解析不到单位会抛 400 并提示可用单位；
     * 这里只记 {@code warn} 然后落 null。原因：前端明细的单位下拉只有
     * {@code 台/个/箱/件/瓶/套/米/千克}，而 {@code sys_unit} 种子只有 {@code 个/台/套/年/次}
     * （产品模块文档 07 已记录的已知问题）—— 用户随手选个「箱」，
     * 严格模式下**整张单据都存不下去**，代价远大于收益。单位不参与任何金额计算，
     * 所以宁可丢一个展示字段，也不要挡住业务。
     */
    private Long resolveUnitId(DocumentItemSaveReqVO row, ProductMiniDO product,
                               Map<String, Long> unitIdByName, Set<Long> explicitUnitIds) {
        if (row.getUnitId() != null && explicitUnitIds.contains(row.getUnitId())) {
            return row.getUnitId();
        }
        if (StringUtils.hasText(row.getUnitName())) {
            String unitName = row.getUnitName().trim();
            Long unitId = unitIdByName.get(unitName);
            if (unitId != null) {
                return unitId;
            }
            log.warn("[明细] 计量单位「{}」不在 sys_unit 中，unit_id 置空（单位不参与金额计算）", unitName);
            return null;
        }
        return product == null ? null : product.getUnitId();
    }

    /** 批量取明细里出现过的单位名 → {@code unit_id}，避免逐行查库 */
    private Map<String, Long> loadUnitIdByName(List<DocumentItemSaveReqVO> rows) {
        Set<String> unitNames = rows.stream()
                .map(DocumentItemSaveReqVO::getUnitName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (unitNames.isEmpty()) {
            return Collections.emptyMap();
        }
        // ⚠️ sys_unit.unit_name 没有唯一索引，所以用合并函数取第一个；
        //    整体也不能用 selectOne（多条会抛 TooManyResultsException）
        return sysUnitMiniMapper.selectList(Wrappers.<SysUnitMiniDO>lambdaQuery()
                        .in(SysUnitMiniDO::getUnitName, unitNames)
                        .orderByAsc(SysUnitMiniDO::getUnitId))
                .stream()
                .filter(unit -> unit.getUnitName() != null && unit.getUnitId() != null)
                .collect(Collectors.toMap(SysUnitMiniDO::getUnitName, SysUnitMiniDO::getUnitId, (a, b) -> a));
    }

    /** 批量校验请求里显式传来的 {@code unitId} 是否真实存在（防塞一个不存在的单位 id） */
    private Set<Long> loadExplicitUnitIds(List<DocumentItemSaveReqVO> rows) {
        List<Long> unitIds = rows.stream()
                .map(DocumentItemSaveReqVO::getUnitId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (unitIds.isEmpty()) {
            return Collections.emptySet();
        }
        return sysUnitMiniMapper.selectList(SysUnitMiniDO::getUnitId, unitIds).stream()
                .map(SysUnitMiniDO::getUnitId)
                .collect(Collectors.toSet());
    }
}
