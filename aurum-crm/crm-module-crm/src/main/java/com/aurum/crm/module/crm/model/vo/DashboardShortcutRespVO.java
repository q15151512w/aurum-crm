package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 首页看板「快捷入口」的一个入口，对应 {@code GET /dashboard/shortcuts}（《05-接口文档》9.6）。
 *
 * <p>数据源是 {@code sys_menu}，**按当前登录人的权限过滤** ——
 * 没有该菜单权限的入口不下发。所以不同角色登录后看到的入口数量不同。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "首页看板-快捷入口")
public class DashboardShortcutRespVO {

    @Schema(description = "入口名称", example = "线索")
    private String name;

    /**
     * Element Plus 图标组件名。
     *
     * <p>取自 {@code sys_menu.icon} —— 与左侧导航用的是同一套命名，
     * 前端 {@code <component :is="icon">} 直接渲染，所以菜单里配的图标名必须真的存在，
     * 否则前端渲染成空白。`check:static` 有「看板图标名都存在」这一项在守。
     */
    @Schema(description = "Element Plus 图标组件名", example = "Promotion")
    private String icon;

    /**
     * 图标底色（十六进制）。
     *
     * <p>⚠️ {@code sys_menu} **没有颜色列**，所以这里由后端按入口顺序从固定调色板取，
     * 保证同一份菜单每次返回的配色稳定（不会刷新一次换一个颜色）。
     * 前端对 null 会兜底成主色。
     */
    @Schema(description = "图标底色", example = "#28D094")
    private String color;

    @Schema(description = "跳转路由，与 sys_menu.path 一致", example = "/crm/lead")
    private String path;
}
