package com.aurum.crm.module.system.service.impl;

import com.aurum.crm.common.core.TreeUtils;
import com.aurum.crm.module.system.core.AuthConstants;
import com.aurum.crm.module.system.core.DisplayEnumTranslator;
import com.aurum.crm.module.system.dal.dataobject.SysDeptDO;
import com.aurum.crm.module.system.dal.dataobject.SysMenuDO;
import com.aurum.crm.module.system.dal.dataobject.SysUserDO;
import com.aurum.crm.module.system.dal.mysql.SysDeptMapper;
import com.aurum.crm.module.system.dal.mysql.SysMenuMapper;
import com.aurum.crm.module.system.dal.mysql.SysUserMapper;
import com.aurum.crm.module.system.model.vo.DeptTreeNodeVO;
import com.aurum.crm.module.system.model.vo.MenuTreeVO;
import com.aurum.crm.module.system.service.TreeService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 两棵树：部门树与菜单树。都是表单的前置依赖。
 *
 * <ul>
 *     <li>部门树 → 人员表单选部门、角色「自定义数据范围」选部门</li>
 *     <li>菜单树 → 角色配置的权限勾选</li>
 * </ul>
 *
 * <p>树算法统一走 {@link TreeUtils}（全量排序后单遍挂接、孤儿提升为根、防成环）。
 */
@Slf4j
@Service
public class TreeServiceImpl implements TreeService {

    @Resource
    private SysDeptMapper sysDeptMapper;
    @Resource
    private SysMenuMapper sysMenuMapper;
    /** 部门树要显示「负责人」姓名，需要查 sys_user */
    @Resource
    private SysUserMapper sysUserMapper;

    /**
     * 部门树。
     *
     * <p>⚠️ 节点字段是 {@code id} / {@code name}，不是 {@code deptId} / {@code deptName} ——
     * 见 {@link DeptTreeNodeVO} 的说明。
     *
     * <p>只取 `status = 1`（正常）的部门；停用部门不进树。
     */
    @Override
    public List<DeptTreeNodeVO> deptTree() {
        List<SysDeptDO> depts = sysDeptMapper.selectList(Wrappers.<SysDeptDO>lambdaQuery()
                .eq(SysDeptDO::getStatus, AuthConstants.DEPT_STATUS_NORMAL));

        // 部门 VO 里不带 parentId（前端不需要它），但建树必须知道父子关系 ——
        // 先把它抽成一个 Map，避免建树时为每个节点去 List 里扫一遍（那是 O(n²)）
        Map<Long, Long> parentIdByDeptId = depts.stream()
                .filter(d -> d.getDeptId() != null)
                .collect(Collectors.toMap(SysDeptDO::getDeptId, SysDeptDO::getParentId, (a, b) -> a));

        // 负责人姓名：先把所有 leader_user_id 收起来一次查完，不要每行查一次（N+1）
        Set<Long> leaderIds = depts.stream().map(SysDeptDO::getLeaderUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> leaderNames = leaderIds.isEmpty() ? Map.of()
                : sysUserMapper.selectList(SysUserDO::getUserId, leaderIds).stream()
                .collect(Collectors.toMap(SysUserDO::getUserId, SysUserDO::getRealName, (a, b) -> a));

        List<DeptTreeNodeVO> nodes = new ArrayList<>(depts.size());
        for (SysDeptDO dept : depts) {
            DeptTreeNodeVO vo = new DeptTreeNodeVO();
            vo.setId(dept.getDeptId());
            vo.setName(dept.getDeptName());
            // 码值 → 英文字符串（前端拿它当 CSS 类名 + 徽标文案）
            vo.setType(DisplayEnumTranslator.deptTypeToText(dept.getType()));
            // 未设负责人给空串：前端模板是 `{{ data.leaderName || '--' }}`，
            // 传 null 会渲染成 "null" 而不是走兜底
            //
            // ⚠️ 不能直接 getOrDefault：leaderNames 在「所有部门都没设负责人」时是
            //    Map.of()（不可变），而 getOrDefault(null, x) 内部会 requireNonNull(key) 抛 NPE。
            //    先判 key 再查。（同一个坑在 UserVoAssembler 也踩过一次，那里有详细注释）
            vo.setLeaderName(dept.getLeaderUserId() == null
                    ? ""
                    : leaderNames.getOrDefault(dept.getLeaderUserId(), ""));
            vo.setChildren(new ArrayList<>());
            nodes.add(vo);
        }

        return TreeUtils.build(
                nodes,
                DeptTreeNodeVO::getId,
                vo -> parentIdByDeptId.getOrDefault(vo.getId(), AuthConstants.MENU_ROOT_PARENT_ID),
                DeptTreeNodeVO::getChildren,
                DeptTreeNodeVO::setChildren,
                Comparator.comparing(DeptTreeNodeVO::getId),
                AuthConstants.MENU_ROOT_PARENT_ID);
    }

    /**
     * 菜单树（全量，不做角色过滤）。
     *
     * <p>用途是角色配置页的权限勾选树 —— 要显示**全部**可选权限，
     * 而不是当前用户自己有的权限。这与 {@code /auth/me} 的菜单树语义相反，
     * 所以不能复用 {@code AuthServiceImpl.buildMenuTree}（那个是按角色过滤的）。
     *
     * <p>⚠️ 不过滤 `visible`：隐藏页（如详情页）也是可授权的路由，
     * 过滤掉的话详情页会变成谁都进不去。
     */
    @Override
    public List<MenuTreeVO> menuTree() {
        List<SysMenuDO> menus = sysMenuMapper.selectList(Wrappers.<SysMenuDO>lambdaQuery()
                .eq(SysMenuDO::getStatus, AuthConstants.MENU_STATUS_NORMAL));

        List<MenuTreeVO> nodes = new ArrayList<>(menus.size());
        for (SysMenuDO menu : menus) {
            MenuTreeVO vo = new MenuTreeVO();
            vo.setMenuId(menu.getMenuId());
            vo.setParentId(menu.getParentId());
            vo.setMenuName(menu.getMenuName());
            vo.setMenuType(menu.getMenuType());
            vo.setPerms(menu.getPerms());
            vo.setIcon(menu.getIcon());
            vo.setSort(menu.getSort());
            vo.setVisible(menu.getVisible());
            vo.setChildren(new ArrayList<>());
            nodes.add(vo);
        }

        return TreeUtils.build(
                nodes,
                MenuTreeVO::getMenuId,
                MenuTreeVO::getParentId,
                MenuTreeVO::getChildren,
                MenuTreeVO::setChildren,
                // 与 AuthServiceImpl.buildMenuTree 保持同一套排序：sort 升序，同序用 id 兜底
                Comparator.comparing(MenuTreeVO::getSort, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(MenuTreeVO::getMenuId, Comparator.nullsLast(Long::compareTo)),
                AuthConstants.MENU_ROOT_PARENT_ID);
    }
}
