package com.aurum.crm.module.system.dal.dataobject;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单 DO，对应表 {@code sys_menu}。
 *
 * <p>这是 RBAC 里「权限」的载体：{@code menu_type = 3} 的按钮行就是一条权限标识。
 * {@code /auth/me} 下发给前端的菜单树、以及 permissions 数组，都从这张表来。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenuDO extends BaseDO {

    /** 菜单 ID */
    @TableId
    private Long menuId;

    /** 父菜单 ID（0 为顶级） */
    private Long parentId;

    /** 菜单名称 */
    private String menuName;

    /** 类型：1 目录，2 菜单，3 按钮 */
    private Integer menuType;

    /** 路由地址：目录为 null，菜单为绝对路径（如 {@code /crm/lead}），按钮为 null */
    private String path;

    /** 前端组件路径（相对 {@code src/views}，无扩展名），如 {@code crm/lead/index} */
    private String component;

    /** 权限标识，如 {@code crm:customer:list}；目录上是段级标识如 {@code crm:lead} */
    private String perms;

    /** 菜单图标（Element Plus 图标名） */
    private String icon;

    /** 显示排序 */
    private Integer sort;

    /**
     * 是否显示：<b>0 隐藏，1 显示</b>。
     *
     * <p>⚠️ {@code visible = 0} 是「不下发到侧边栏但路由必须存在」的隐藏页
     * （如详情页 {@code /crm/lead/:id}）。构建菜单树时<b>绝不能按它过滤</b>，
     * 否则前端拿不到这条路由，跳转详情页直接 404。
     */
    private Integer visible;

    /** 状态：0 停用，1 正常（停用的菜单不下发） */
    private Integer status;
}
