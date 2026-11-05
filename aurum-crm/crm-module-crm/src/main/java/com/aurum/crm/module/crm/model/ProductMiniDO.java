package com.aurum.crm.module.crm.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 产品精简投影，供报价单明细使用：产品名称、分类（做分类快照）、计量单位、**成本价**（算毛利）。
 *
 * <p>与 {@code SysUserMiniDO} / {@code CustomerMiniDO} 同一套「只读投影」做法。
 *
 * <p><b>⚠️ 刻意不继承 {@code BaseDO}</b>，与 {@code CustomerMiniDO} 同理，但这里的理由更硬：
 * <ul>
 *     <li>继承 {@code BaseDO} 会带上 {@code @TableLogic}，查询自动追加 {@code del_flag = 0}
 *       —— 那样**产品被逻辑删除后，历史报价单的整单利润就会算不出来**。
 *       成本是历史事实，产品下架/删除不代表这张报价单当初的成本不存在</li>
 *     <li>报价单只需要 4 列，直接读 {@code ProductDO} 会把成本价之外的描述、图片等一起读进来</li>
 * </ul>
 *
 * <p>代价是这里读到的可能是已下架/已删除的产品 —— 这正是我们要的（见上）。
 */
@Data
@TableName("crm_product")
public class ProductMiniDO implements Serializable {

    /** 产品ID */
    @TableId
    private Long productId;

    /** 产品名称（备用；明细的产品名以客户端提交的快照为准） */
    private String productName;

    /** 产品分类ID，写进明细做分类快照 */
    private Long categoryId;

    /** 计量单位ID，明细没给单位名时兜底 */
    private Long unitId;

    /** 成本价（{@code DECIMAL(15,2) NOT NULL DEFAULT 0.00}，是算毛利的唯一来源） */
    private BigDecimal costPrice;
}
