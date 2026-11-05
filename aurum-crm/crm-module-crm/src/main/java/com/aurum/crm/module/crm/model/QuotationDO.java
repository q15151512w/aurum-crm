package com.aurum.crm.module.crm.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 报价单 DO，对应表 {@code crm_quotation}。
 *
 * <p><b>字段名与数据库列严格一一对应</b>：DO 只负责搬运，对外叫什么名字由
 * {@link com.aurum.crm.module.crm.model.convert.QuotationConvert} 决定
 * （对外一律用 {@code crm-ui} 前端的叫法，对照表见 {@code docs/11-报价单模块字段契约.md}）。
 *
 * <p><b>⚠️ 三个金额列的口径（《02-建表文档》3.6）</b>：
 * <ul>
 *     <li>{@code total_amount} —— **不含税**合计（= 价税合计 − 税额）。
 *       注意与 {@code fin_invoice.total_amount}（价税合计）**同名不同义**</li>
 *     <li>{@code tax_amount} —— 税额合计</li>
 *     <li>{@code total_with_tax} —— 价税合计</li>
 * </ul>
 * 三个值一律由 {@code MoneyCalculator} 从明细重算，**不采信客户端提交的金额**。
 *
 * <p><b>注意</b>：本类<b>不能</b>声明 {@code isActive} 字段 —— 它是数据库生成列
 * （{@code IF(del_flag = 0, 1, NULL)}），只读，
 * 写进去会报 {@code The value specified for generated column ... is not allowed}。
 *
 * <p><b>没有 profit / discount 列</b>：整单利润与整单折扣在库里都没有落点
 * （前者读取时现算，后者恒为 0），见字段契约文档的「待建表」与「有意偏差」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_quotation")
public class QuotationDO extends BaseDO implements Serializable {

    /** 报价单ID */
    @TableId
    private Long quotationId;

    /** 报价单编号，业务唯一键，由服务端生成（{@code "BJ" + 雪花ID}） */
    private String quotationNo;

    /** 报价单名称 */
    private String quotationName;

    /** 关联商机ID（可空 —— 可以不挂商机直接给客户报价） */
    private Long opportunityId;

    /** 关联客户ID（必填） */
    private Long customerId;

    /**
     * 版本号 —— 「一个商机可出多版报价单」（《02-建表文档》3.x）。
     *
     * <p>新建时按同商机下现有最大版本 +1 自动维护；前端不可见、不可改。
     */
    private Integer version;

    /** 金额合计（**不含税** = 价税合计 − 税额） */
    private BigDecimal totalAmount;

    /** 税额合计 */
    private BigDecimal taxAmount;

    /** 价税合计（对外的 {@code finalAmount}） */
    private BigDecimal totalWithTax;

    /** 有效期至（前端暂无入口，API 侧可传） */
    private LocalDate validDate;

    /** 归属人ID（必填，报价单没有公海） */
    private Long ownerUserId;

    /** 归属部门ID，跟着**负责人**所在部门走（不是创建人） */
    private Long deptId;

    /** 状态（0草稿 1已发出 2已接受 3已拒绝 4已作废）—— 对外是 {@link QuotationStatus} 的中文标签 */
    private Integer status;

    /** 备注 */
    private String remark;
}
