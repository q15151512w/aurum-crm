package com.aurum.crm.module.crm.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商机新增 / 修改请求，字段名与前端表单模型**逐字一致**。
 *
 * <p>前端提交体就是 {@code views/crm/opportunity/form-config.js} 的 {@code emptyForm()}
 * 声明过的那几个键（{@code useFormDialog} 提交前会摘掉 {@code id}），
 * 所以这里的字段名必须与它一模一样，前端一行都不用改。
 *
 * <p><b>几个与后端存储的对应关系</b>：
 * <ul>
 *     <li>{@code name} → {@code crm_opportunity.opportunity_name}</li>
 *     <li>{@code customerId} → {@code customer_id}（选择器的 {@code extraProp} 写入；
 *       表单里同时写进 {@code customerName} 只用于显示，后端不接收）</li>
 *     <li>{@code ownerUserId} → {@code owner_user_id}（同上，{@code ownerName} 只是显示文本）。
 *       不传时缺省为**当前登录人**</li>
 *     <li>{@code stage} → {@code stage}：请求里是**中文标签**，服务端转成 TINYINT 码值</li>
 *     <li>{@code expectDealTime} → {@code expected_close_date}：请求里是字符串，
 *       见下方「为什么日期是 String」</li>
 *     <li>{@code totalAmount} → {@code amount}：<b>含税</b>合计总价。
 *       ⚠️ **该商机已有产品明细时，提交值会被明细汇总覆盖**（明细才是权威，明细只能通过
 *       {@code POST/PUT /crm/opportunity/{id}/products} 维护）。没有明细时按提交值落库
 *       （先填预估、后补明细的销售习惯保留）。细则见
 *       {@code docs/13-商机与报价单金额口径总览.md}</li>
 * </ul>
 *
 * <p><b>为什么日期是 {@code String} 而不是 {@code LocalDate}</b>：
 * 前端 {@code emptyForm()} 把 {@code expectDealTime} 初始化为**空字符串** {@code ''}
 * （{@code el-date-picker} 清空时给的是 {@code null}，从没选过则一直是 {@code ''}），
 * 而 Jackson 默认会把 {@code ""} 交给 {@code LocalDate.parse} 从而抛
 * {@code DateTimeParseException} → 用户一打开新增弹窗、什么都不填就提交，直接 400。
 * 全局开启 {@code ACCEPT_EMPTY_STRING_AS_NULL_OBJECT} 能解决，但那要改共享的
 * {@code application.yml}（本次刻意不动已有代码）。所以这里收字符串，
 * 由 Service 用 {@code parseDateLeniently} 宽容解析：空白 → {@code null}，非法 → 400 带明确文案。
 *
 * <p><b>刻意不声明的字段（会被静默忽略）</b>：{@code customerName} / {@code ownerName}
 * （选择器写入的显示文本）、{@code nextContactTime}（没有对应列）、
 * {@code finalAmount}（由 {@code totalAmount} 与 {@code discount} 派生，不落库）、
 * <b>{@code products}</b>（明细行数组）。前端商机表单为了在弹窗里算出「合计总价」会把
 * {@code products} 一起提交上来，这里刻意不声明 —— 明细**只能**通过
 * {@code POST/PUT /crm/opportunity/{id}/products} 写入（那边有行数上限、单位解析、
 * 行金额重算与「合计总价回写」一整套逻辑），在这里再收一份就会变成两套写路径。
 * Spring Boot 默认关闭了 {@code FAIL_ON_UNKNOWN_PROPERTIES}，多余字段不会报错
 * —— 产品模块的 {@code categoryName} 也是同一处理方式。
 */
@Data
@Schema(description = "商机新增/修改")
public class OpportunitySaveReqVO {

    @Schema(description = "商机名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "星辰科技 CRM 标准版采购")
    @NotBlank(message = "商机名称不能为空")
    @Size(max = 128, message = "商机名称长度不能超过 128")
    private String name;

    @Schema(description = "关联客户ID（客户选择器写入）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择客户")
    private Long customerId;

    @Schema(description = "负责人ID；不传时缺省为当前登录人")
    private Long ownerUserId;

    @Schema(description = "销售阶段（中文标签），不传时缺省「初步接洽」", example = "初步接洽")
    @Size(max = 16, message = "阶段取值过长")
    private String stage;

    @Schema(description = "预计成交时间（yyyy-MM-dd；空串表示清空该字段）", example = "2026-10-01")
    @Size(max = 32, message = "预计成交时间格式不正确")
    private String expectDealTime;

    @Schema(description = "下次联系时间（yyyy-MM-dd；空串表示清空该字段）", example = "2026-09-20")
    @Size(max = 32, message = "下次联系时间格式不正确")
    private String nextContactTime;

    /**
     * 合计总价（含税），对应 {@code crm_opportunity.amount}，缺省 0。
     *
     * <p><b>⚠️ 有产品明细时这个值会被明细汇总覆盖</b>：明细（
     * {@code POST/PUT /crm/opportunity/{id}/products}）才是权威 —— 提交的金额与明细汇总不一致时
     * 服务端按明细汇总落库并记一条 {@code debug} 日志，**不返回 400**
     * （与报价单「客户端提交的金额一律被覆盖」同一取舍：服务端重算已经保证库里是对的，
     * 严格校验只会把用户的保存卡死）。前端在「有明细」时把这一格置灰，
     * 用户看到的就是明细汇总值。
     *
     * <p>没有明细时按提交值落库 —— 保留「先填预估金额、之后再补明细」的销售习惯。
     */
    @Schema(description = "合计总价（含税）。有产品明细时会被明细汇总覆盖；无明细时按提交值落库",
            example = "41800.00")
    @DecimalMin(value = "0", message = "合计总价不能为负数")
    @Digits(integer = 13, fraction = 2, message = "合计总价最多 13 位整数、2 位小数")
    private BigDecimal totalAmount;

    /**
     * 整单折扣(%)，对应 {@code crm_opportunity.discount_rate}（原来是 {@code win_rate}）。
     *
     * <p>取值 0–100（前端表单的输入框就是 {@code min:0 max:100}，UI 只填整数，
     * 但这里允许 2 位小数）。缺省 0 = 不打折。
     *
     * <p>它只影响对外的「整单最终价」{@code finalAmount = totalAmount × (1 − discount/100)}；
     * {@code totalAmount}（合计总价）本身不变。
     *
     * <p><b>编辑时不传 = 不改这一列</b>（靠全局 {@code update-strategy: not_null}）；
     * 若前端要清成 0，显式传 {@code 0} 即可（前端的表单默认值就是 0，所以总会传）。
     */
    @Schema(description = "整单折扣(%)，0–100，缺省 0（不落 NULL，读时 NULL 归一成 0）", example = "15")
    @DecimalMin(value = "0", message = "整单折扣不能为负数")
    @DecimalMax(value = "100", message = "整单折扣不能超过 100")
    @Digits(integer = 3, fraction = 2, message = "整单折扣最多 3 位整数、2 位小数")
    private BigDecimal discount;

    @Schema(description = "备注（前端表单暂未开放该项）")
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
