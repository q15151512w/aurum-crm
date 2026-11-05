package com.aurum.crm.module.crm.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 产品分类 DO，对应表 {@code crm_product_category}。
 *
 * <p>树形结构用 **{@code parent_id} + {@code ancestors} 双字段**表达，
 * 用法与 {@code sys_dept} 完全一致（《02-建表文档》3.2 节同类约定）：
 * <pre>
 *   category_id  parent_id  ancestors      含义
 *        1           0      "0"            顶级分类（ancestors 只放占位 0）
 *        4           1      "0,1"          1 的子分类
 *       11           4      "0,1,4"        二级子分类
 * </pre>
 *
 * <p>为什么要冗余 {@code ancestors}：查「某分类及其所有子孙」时可以直接做
 * 前缀匹配（{@code ancestors = '0,1' OR ancestors LIKE '0,1,%'}），
 * <b>不用递归查树</b>，也就不用递归 SQL / N 次查询。
 *
 * <p>代价是「移动分类」时必须连带重算整棵子树的 {@code ancestors}，
 * 这段逻辑集中在 {@code ProductCategoryServiceImpl#moveDescendants}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_product_category")
public class ProductCategoryDO extends BaseDO implements Serializable {

    /** 分类ID */
    @TableId
    private Long categoryId;

    /** 父分类ID（0 表示顶级） */
    private Long parentId;

    /** 祖级列表（逗号分隔，如 {@code "0,1"}）；顶级分类固定为 {@code "0"} */
    private String ancestors;

    /** 分类名称 */
    private String categoryName;

    /** 排序（升序） */
    private Integer sort;

    /** 状态：0 停用，1 启用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
