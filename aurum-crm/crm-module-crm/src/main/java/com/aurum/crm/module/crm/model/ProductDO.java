package com.aurum.crm.module.crm.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 产品 DO，对应表 {@code crm_product}。
 *
 * <p>产品是**全局目录**：表里没有 {@code owner_user_id} / {@code dept_id}，
 * 因此不参与数据权限过滤（《04-权限设计文档》3.4 的参与表清单也不含本表），
 * 所有登录用户看到同一份产品目录，靠菜单权限约束「能做什么操作」。
 *
 * <p><b>注意</b>：本类<b>不能</b>声明 {@code isActive} 字段 ——
 * 它是数据库生成列（`IF(del_flag = 0, 1, NULL)`），只读，
 * 写进去会报 {@code The value specified for generated column ... is not allowed}。
 * 它的作用是配合 {@code uk_product_no(product_no, is_active)}，
 * 让逻辑删除后的产品编号可以被重新使用（唯一索引中 NULL 之间不冲突）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_product")
public class ProductDO extends BaseDO implements Serializable {

    /** 产品ID */
    @TableId
    private Long productId;

    /**
     * 产品编号（业务唯一键，由服务端生成，前端不可传入）。
     *
     * <p>当前为 {@code "CP" + 雪花ID}，与客户模块的占位做法一致；
     * 待 system 模块的编号规则引擎落地后，只需替换
     * {@code ProductServiceImpl#generateProductNo()} 一处。
     */
    private String productNo;

    /** 产品名称 */
    private String productName;

    /** 产品分类ID（crm_product_category.category_id） */
    private Long categoryId;

    /** 计量单位ID（sys_unit.unit_id） */
    private Long unitId;

    /** 标准售价（含税单价，元） */
    private BigDecimal price;

    /** 成本价（字段级权限保护对象，见 ProductServiceImpl#maskCostPriceIfNeeded） */
    private BigDecimal costPrice;

    /** 税率（%），如 13.00 表示 13% */
    private BigDecimal taxRate;

    /** 产品图片 URL */
    private String image;

    /** 产品描述 */
    private String description;

    /** 上下架状态：0 下架，1 上架 */
    private Integer status;

    /** 备注 */
    private String remark;
}
