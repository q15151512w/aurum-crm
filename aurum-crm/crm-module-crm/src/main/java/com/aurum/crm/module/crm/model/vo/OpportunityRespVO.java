package com.aurum.crm.module.crm.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商机响应（列表 / 详情共用同一套字段名）。
 *
 * <p><b>字段名对齐前端</b>：与 {@code crm-ui} 商机列表的表格列、详情页的描述项、
 * 新增/编辑表单逐项对应，因此前端不需要任何字段映射层
 * （{@code useTable} 还要求每行必须有 {@code id}）。
 * 对照表见 {@code docs/09-商机模块字段契约.md}。
 *
 * <p>{@code customerName} / {@code ownerName} / {@code creator} 不是商机表字段，
 * 由 Service 跨表批量补值（客户、负责人、创建人），避免前端逐行再发请求。
 *
 * <p><b>{@code discount} / {@code finalAmount} / {@code productKinds} /
 * {@code productCount} 是「前端有、库里有别的列」的字段</b>，
 * 当前返回派生值或空值，取值规则见
 * {@code docs/09-商机模块字段契约.md} 的「待建表清单」。
 *
 * <p>{@code nextContactTime} 原属「库里还没有」的字段，已随 BUG-002 的字段对齐补上
 * {@code crm_opportunity.next_contact_time} 列，现在直接映射 DO。
 */
@Data
@Schema(description = "商机响应")
public class OpportunityRespVO {

    @Schema(description = "商机ID")
    private Long id;

    @Schema(description = "商机名称")
    private String name;

    @Schema(description = "商机编号（对应 crm_opportunity.opportunity_no）")
    private String opportunityNo;

    @Schema(description = "关联客户ID")
    private Long customerId;

    @Schema(description = "客户名称（跨表补值）")
    private String customerName;

    @Schema(description = "负责人ID")
    private Long ownerUserId;

    @Schema(description = "负责人姓名（跨表补值，owner_user_id → sys_user.real_name）")
    private String ownerName;

    @Schema(description = "销售阶段（中文标签，前端标签配色按它匹配）", example = "商务谈判")
    private String stage;

    @Schema(description = "预计成交时间（对应 expected_close_date）", example = "2026-10-01")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expectDealTime;

    /**
     * 下次联系时间。
     *
     * <p>⚠️ {@code crm_opportunity} **没有**这一列（{@code crm_follow_up} 也还没建），
     * 因此恒为 {@code null}，而全局 {@code default-property-inclusion: non_null}
     * 会让它从 JSON 里消失，前端表格由 {@code dash()} 兜底显示 {@code --}。
     */
    @Schema(description = "下次联系时间")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate nextContactTime;

    /**
     * 合计总价（对应 {@code crm_opportunity.amount}）。
     *
     * <p><b>口径（本轮定稿）</b>：**有产品明细时 = Σ 明细行的含税金额**
     * （读库里已按 3.6 逐行四舍五入的 {@code crm_opportunity_item.amount} 加总）；
     * **没有明细时 = 商机表单里手填的预计金额**。
     *
     * <p>⚠️ 它是**含税**口径（等于明细的价税合计），与报价单的 {@code total_with_tax} 同基数；
     * 报价单的 {@code totalAmount} 是**不含税**合计，两者不要直接比。
     * 明细 → 主表的回写点在 {@code OpportunityServiceImpl#syncAmountFromItems}，
     * 链路见 {@code docs/13-商机与报价单金额口径总览.md}。
     */
    @Schema(description = "合计总价（含税）。有产品明细时 = 明细行金额汇总，无明细时 = 手填的预计金额")
    private BigDecimal totalAmount;

    /**
     * 整单折扣(%)，0–100。
     *
     * <p>对应 {@code crm_opportunity.discount_rate}（这一列原来是 {@code win_rate}，后被改名）。
     * 前端表单可填、列表有「整单折扣(%)」列。
     *
     * <p>真库上该列**可空**，读路径把 {@code NULL} 归一成 {@code 0}
     * （= 不打折），所以响应里总是有值、列表总能显示成 {@code 0%} 而不是 {@code --}。
     */
    @Schema(description = "整单折扣(%)（对应 crm_opportunity.discount_rate，NULL 归一成 0）", example = "15")
    private BigDecimal discount;

    /**
     * 整单最终价。
     *
     * <p><b>派生值</b>：{@code totalAmount × (1 − discount / 100)}，**不落库**
     * （{@code crm_opportunity} 没有 final_amount 列）。
     * 与前端 {@code form-config.js} 里 {@code finalAmount} 的 {@code compute} 是同一公式。
     *
     * <p>注意 {@code totalAmount}（合计总价）本身**不打折**，只有本字段是折后价。
     */
    @Schema(description = "整单最终价（派生：合计总价 ×(1−折扣/100)，不落库）")
    private BigDecimal finalAmount;

    /**
     * 商品种类数 = 明细里**不同产品**的个数。
     *
     * <p>口径：有 {@code productId} 的按 `productId` 去重；没有 `productId` 的手填行各算一种
     *（按 `item_id` 去重），免得几行手填行被并成 1 种。
     *
     * <p>由 Service 从 `crm_opportunity_item` 现算（列表批量取、详情取单个），**不落库**。
     */
    @Schema(description = "商品种类数（= 明细里不同产品的个数）")
    private Integer productKinds;

    /**
     * 商品总数 = 明细数量合计 `SUM(quantity)`（去掉无意义尾零）。
     *
     * <p>与报价单的「商品总数」同一个口径；数量支持 4 位小数，所以这里用 {@link BigDecimal}
     * 而不是整数（如 `2.5` 米）。
     */
    @Schema(description = "商品总数（= 明细数量合计 SUM(quantity)，可能有小数）")
    private BigDecimal productCount;

    /**
     * 产品明细（`crm_opportunity_item`）。
     *
     * <p><b>详情必须返回它</b>：前端详情页的「添加产品」弹窗是用 `base.products` 回填编辑器的，
     * 保存时走 `PUT /crm/opportunity/{id}/products` **整份覆盖** —— 少回填一行就等于把那行洗掉。
     * 列表也一并返回，方便前端直接用。
     */
    @Schema(description = "产品明细（详情返回；列表也返回，方便前端直接用）")
    private List<DocumentItemRespVO> products;

    @Schema(description = "实际成交日期（赢单时自动落当天）", example = "2026-10-08")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate actualCloseDate;

    @Schema(description = "创建人姓名（跨表补值，create_by → sys_user.real_name）")
    private String creator;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 当前登录人是否已关注该商机（{@code crm_star}，{@code biz_type = 3}）。
     *
     * <p>不是商机表的列，由 Service 查 {@code StarService} 补值：列表批量查一次
     * （{@code starredBizIds(bizType, ids)}，避免逐行查库），详情查单条。
     * 前端 {@code ProList} 的星标列与「我关注的商机」筛选胶囊都读它。
     *
     * <p>未关注时是 {@code false} 而不是 {@code null} —— 关闭星标是一个明确的状态，
     * 给 {@code null} 会让前端的三元判断多一层兜底。
     */
    @Schema(description = "当前登录人是否已关注该商机")
    private Boolean starred;

    @Schema(description = "备注")
    private String remark;
}
