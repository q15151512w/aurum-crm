package com.aurum.crm.module.crm.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报价单响应（列表 / 详情共用同一套字段名）。
 *
 * <p><b>字段名对齐前端</b>：与 {@code crm-ui} 报价单列表的 14 个表格列、新增/编辑表单逐项对应，
 * 前端不需要任何字段映射层（{@code useTable} 要求每行必须有 {@code id}，
 * 编辑流程还要求详情返回 {@code products}）。对照表见 {@code docs/11-报价单模块字段契约.md}。
 *
 * <p><b>⚠️ 金额字段的口径（《02-建表文档》3.6）</b>：{@code totalAmount} 是**不含税**合计、
 * {@code finalAmount} 才是含税价税合计。这与 {@code fin_invoice.total_amount}（价税合计）
 * **同名不同义**，改动前先看字段契约文档。
 *
 * <p>{@code customerName} / {@code opportunityName} / {@code ownerName} / {@code creator} /
 * {@code productCount} / {@code profit} 都不是报价单表字段，由 Service 跨表批量补值或按明细派生。
 */
@Data
@Schema(description = "报价单响应")
public class QuotationRespVO {

    @Schema(description = "报价单ID")
    private Long id;

    @Schema(description = "报价单名称")
    private String name;

    @Schema(description = "报价单编号（对应 crm_quotation.quotation_no）")
    private String quotationNo;

    @Schema(description = "关联商机ID")
    private Long opportunityId;

    @Schema(description = "关联商机名称（跨表补值；商机被逻辑删除时为 null）")
    private String opportunityName;

    @Schema(description = "关联客户ID")
    private Long customerId;

    @Schema(description = "客户名称（跨表补值）")
    private String customerName;

    @Schema(description = "负责人ID")
    private Long ownerUserId;

    @Schema(description = "负责人姓名（跨表补值）")
    private String ownerName;

    @Schema(description = "状态（中文标签，前端标签配色按它匹配）", example = "报价中")
    private String status;

    /**
     * 报价日期。
     *
     * <p>⚠️ {@code crm_quotation} 没有这一列，这里**派生自 `create_time` 的日期部分**
     * （「报价日期就是建单日期」）。表单里改这一天不会生效，见字段契约文档。
     */
    @Schema(description = "报价日期（⚠️ 派生自 createTime，无对应列）", example = "2026-09-14")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate quoteDate;

    @Schema(description = "有效期至（对应 valid_date；前端暂无展示位）", example = "2026-10-14")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate validDate;

    @Schema(description = "合计价格（**不含税**，对应 total_amount）")
    private BigDecimal totalAmount;

    @Schema(description = "税额（对应 tax_amount）")
    private BigDecimal taxAmount;

    /**
     * 整单最终价（**折后**）。
     *
     * <p>派生值：{@code total_with_tax × (1 − discount / 100)}，随关联商机的折扣走。
     *
     * <p>⚠️ {@code totalAmount} / {@code taxAmount} 仍是**落库**的「明细汇总」原值
     * （{@code total_amount} / {@code tax_amount}，3.6 口径，**不打折**），
     * 所以响应里会出现 <b>不含税合计 + 税额 ≠ 整单最终价</b> —— 这是刻意的口径，
     * 见 {@code docs/11-报价单模块字段契约.md} 第三节。
     */
    @Schema(description = "整单最终价（折后：价税合计 ×(1−商机折扣/100)）")
    private BigDecimal finalAmount;

    /**
     * 整单折扣(%)。
     *
     * <p><b>统一取关联商机的折扣</b>（{@code crm_opportunity.discount_rate}）——
     * {@code crm_quotation} 自己没有折扣列、表单也没有这一项，要改折扣得去改商机。
     *
     * <p>无关联商机、或商机取不到（已逻辑删除 / 不在数据范围内）→ {@code 0}（= 不打折）。
     */
    @Schema(description = "整单折扣(%)（取关联商机的折扣；无商机或取不到时为 0）", example = "10")
    private BigDecimal discount;

    /**
     * 整单利润（毛利）。
     *
     * <p>读取时现算：{@code Σ(行不含税 − ROUND(成本价 × 数量, 2))}，
     * 成本取 {@code crm_product.cost_price}。
     *
     * <p><b>为 {@code null} 的含义</b>：有任意一行算不出成本（明细没挂 {@code productId}、
     * 或产品查不到）。此时宁可不显示（前端列显示 {@code --}），也不给一个偏高的错数。
     * 又因为全局 {@code default-property-inclusion: non_null}，为 null 时该字段会从 JSON 里消失。
     */
    @Schema(description = "整单利润（毛利）。⚠️ 有明细行缺成本时为 null，见字段契约文档")
    private BigDecimal profit;

    @Schema(description = "商品总数（= 明细数量合计 SUM(quantity)，可能有小数）")
    private BigDecimal productCount;

    @Schema(description = "版本号（补充字段，前端无展示位）")
    private Integer version;

    @Schema(description = "创建人姓名（跨表补值）")
    private String creator;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @Schema(description = "备注")
    private String remark;

    /**
     * 当前登录人是否已关注该报价单（{@code crm_star}，{@code biz_type = 4}）。
     *
     * <p>不是报价单表的列，由 Service 查 {@code StarService} 补值：列表批量查一次
     * （{@code starredBizIds(bizType, ids)}，避免逐行查库），详情查单条。
     * 前端 {@code ProList} 的星标列与「我关注的报价」筛选胶囊都读它。
     *
     * <p>未关注时是 {@code false} 而不是 {@code null} —— 关闭星标是一个明确的状态。
     */
    @Schema(description = "当前登录人是否已关注该报价单")
    private Boolean starred;

    /**
     * 由本报价单生成的最新一份合同 ID（没有合同 → {@code null}）。
     *
     * <p>不是报价单表的列，由 Service 反查 {@code crm_contract.quotation_id} 补值。
     * 一个报价单可以出多份合同（1:N，见
     * {@link com.aurum.crm.module.crm.service.QuotationService#convertToContract}），
     * 这里只给**最新一份**够前端做「点击跳合同详情」的入口；
     * 「一共几份」见 {@link #contractCount}。
     *
     * <p>没有合同时为 {@code null}，在全局 {@code default-property-inclusion: non_null}
     * 下该字段会从 JSON 里消失，前端 {@code dash()} 兜底显示 {@code --}。
     */
    @Schema(description = "由本报价单生成的最新一份合同ID（无合同时为 null）")
    private Long contractId;

    /** 最新一份合同的编号（跨表补值）—— 前端列表「关联合同」列显示它并作为跳转入口 */
    @Schema(description = "最新一份合同的编号（跨表补值）")
    private String contractNo;

    /**
     * 已生成的合同份数。
     *
     * <p>因为允许重复转换（1:N），这个值可能是 2、3……前端在「关联合同」列上用它做提示
     * （「共 N 份，点击查看最新一份」），免得用户以为只能有一份。
     */
    @Schema(description = "已生成的合同份数（1:N，允许重复转换）")
    private Integer contractCount;

    /**
     * 产品明细（含税单价、含税行金额）。
     *
     * <p>详情**必须**返回它：前端的编辑流程是「详情 → 回填 {@code ProductItemEditor} →
     * PUT 整份覆盖」，少了明细一次编辑就会把明细洗掉。
     */
    @Schema(description = "产品明细（详情返回；列表也为空数组，方便前端直接用）")
    private List<DocumentItemRespVO> products;
}
