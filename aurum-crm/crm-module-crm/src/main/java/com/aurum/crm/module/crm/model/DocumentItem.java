package com.aurum.crm.module.crm.model;

import java.math.BigDecimal;

/**
 * 单据明细行的<b>共用字段契约</b> —— `crm_quotation_item` / `crm_opportunity_item` /
 * `crm_contract_item` 三张表结构完全一致（《02-建表文档》3.5：「三张表共用一个明细行结构」）。
 *
 * <p><b>为什么是接口而不是基类</b>：
 * <ul>
 *     <li>{@code @TableName} 只能标在具体类上，所以每张表**必须**有自己的 DO
 *       （{@link QuotationItemDO} / {@link OpportunityItemDO}），基类省不掉这两个类的存在</li>
 *     <li>用接口的话，两个 DO 只要加 {@code implements DocumentItem} 就行 ——
 *       Lombok {@code @Data} 生成的 getter/setter 天然满足它，**字段一个字都不用挪**，
 *       已交付并测过的类不受影响</li>
 *     <li>MyBatis-Plus 读的是<b>字段</b>而不是接口，所以接口对持久化完全无感</li>
 * </ul>
 *
 * <p>它唯一的作用是让 {@code support/ItemSupport} 能对「任意一种明细行」做同一套归一化
 * （默认值兜底 → 产品分类快照 → 单位解析 → 行金额重算 → 排序），避免三份重复代码。
 *
 * <p><b>刻意不包含</b>：主键 {@code itemId}、外键（{@code quotationId} / {@code opportunityId}）、
 * 审计字段 —— 这些由各自的 Service 负责，共用逻辑碰不着。
 */
public interface DocumentItem {

    Long getProductId();

    void setProductId(Long productId);

    String getProductName();

    void setProductName(String productName);

    Long getProductTypeId();

    void setProductTypeId(Long productTypeId);

    Long getUnitId();

    void setUnitId(Long unitId);

    BigDecimal getQuantity();

    void setQuantity(BigDecimal quantity);

    BigDecimal getUnitPrice();

    void setUnitPrice(BigDecimal unitPrice);

    BigDecimal getDiscountRate();

    void setDiscountRate(BigDecimal discountRate);

    BigDecimal getTaxRate();

    void setTaxRate(BigDecimal taxRate);

    BigDecimal getAmount();

    void setAmount(BigDecimal amount);

    Integer getSort();

    void setSort(Integer sort);

    String getRemark();

    void setRemark(String remark);
}
