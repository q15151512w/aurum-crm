package com.aurum.crm.module.crm.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商机产品明细 DO，对应表 {@code crm_opportunity_item}。
 *
 * <p><b>与 {@link QuotationItemDO} 字段逐列一致</b>（《02-建表文档》3.5：「三张表共用一个明细行结构」），
 * 差别只有 {@code @TableName} 与外键列名。共用逻辑在 {@code support/ItemSupport}，
 * 共用字段契约在 {@link DocumentItem}。
 *
 * <p><b>快照原则</b>：{@code productName} / {@code unitPrice} / {@code amount} 都是**快照**，
 * 产品改价或下架后历史明细不能跟着变。
 *
 * <p>{@link #amount} 由服务端按《02-建表文档》3.6 重算并覆盖，不采信客户端提交的值。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_opportunity_item")
public class OpportunityItemDO extends BaseDO implements Serializable, DocumentItem {

    /** 明细ID */
    @TableId
    private Long itemId;

    /** 商机ID */
    private Long opportunityId;

    /** 产品ID（可空 —— 允许手填一个不在产品目录里的名称） */
    private Long productId;

    /** 产品名称**快照**（必填） */
    private String productName;

    /** 产品分类ID快照（前端不传，由产品的 category_id 带出） */
    private Long productTypeId;

    /** 计量单位ID（前端给的是单位**名字**，由服务端解析） */
    private Long unitId;

    /** 数量，支持 4 位小数 */
    private BigDecimal quantity;

    /** 单价（**含税**） */
    private BigDecimal unitPrice;

    /** 折扣率(%) */
    private BigDecimal discountRate;

    /** 税率(%) */
    private BigDecimal taxRate;

    /** 行金额（**含税**），服务端重算 */
    private BigDecimal amount;

    /** 排序（前端不传，按提交顺序写；{@code POST} 追加时从现有最大 sort 续号） */
    private Integer sort;

    /** 备注 */
    private String remark;
}
