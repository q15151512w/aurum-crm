package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点（文档 2.6 的 {@code data.menus}）。
 *
 * <p>这是「菜单权限」的实际落地形态：前端 {@code permissionStore.generateRoutes}
 * 只用 {@code menuType === 2 && path} 的节点生成路由，
 * {@code visible === 0} 视为隐藏页（可路由但不进侧边栏），
 * {@code perms} 会写进路由的 {@code meta.perms}。
 *
 * <p>⚠️ <b>这几个字段一个都不能省</b> —— 少了 {@code menuType} 路由不生成，
 * 少了 {@code visible} 详情页会跑进侧边栏，少了 {@code perms} 按钮权限失效。
 */
@Data
@Schema(description = "菜单树节点")
public class MenuRespVO {

    @Schema(description = "菜单 ID")
    private Long menuId;

    @Schema(description = "父菜单 ID（0 为顶级）")
    private Long parentId;

    @Schema(description = "菜单名称")
    private String menuName;

    @Schema(description = "类型：1 目录，2 菜单，3 按钮")
    private Integer menuType;

    @Schema(description = "路由地址")
    private String path;

    @Schema(description = "前端组件路径")
    private String component;

    @Schema(description = "权限标识")
    private String perms;

    @Schema(description = "菜单图标")
    private String icon;

    @Schema(description = "显示排序")
    private Integer sort;

    @Schema(description = "是否显示：0 隐藏，1 显示")
    private Integer visible;

    /**
     * 子节点。
     *
     * <p>⚠️ <b>必须初始化成空 List，不能是 null</b> —— 前端渲染时直接
     * {@code menu.children.map(...)}，null 会直接 NPE。
     * 注意全局的 {@code default-property-inclusion: non_null} 只省略 null，
     * 不会省略空 List，所以 {@code []} 会正常出现在 JSON 里。
     */
    @Schema(description = "子节点")
    private List<MenuRespVO> children = new ArrayList<>();
}
