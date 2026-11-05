package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点（{@code GET /system/menu/tree}），供角色配置页的权限勾选树使用。
 *
 * <p>字段名与 {@code /auth/me} 下发的菜单节点保持一致（{@code menuId} / {@code menuName} / …），
 * 因为前端那棵树用 {@code node-key="menuId"}、{@code :props="{label:'menuName', children:'children'}"}。
 *
 * <p>⚠️ 与 {@code MenuRespVO} 的区别：那个是「当前用户有权限的菜单」（按角色过滤过），
 * 这个是「全部可授权的菜单」，语义相反，不要合并成一个类。
 */
@Data
@Schema(description = "菜单树节点（权限配置用，全量）")
public class MenuTreeVO {

    @Schema(description = "菜单ID")
    private Long menuId;

    @Schema(description = "父菜单ID")
    private Long parentId;

    @Schema(description = "菜单名称")
    private String menuName;

    @Schema(description = "类型：1 目录 / 2 菜单 / 3 按钮")
    private Integer menuType;

    @Schema(description = "权限标识")
    private String perms;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "是否显示：0 隐藏 / 1 显示")
    private Integer visible;

    /** ⚠️ 必须初始化为空 List，前端靠它判断有无子节点 */
    @Schema(description = "子菜单")
    private List<MenuTreeVO> children = new ArrayList<>();
}
