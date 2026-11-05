package com.aurum.crm.module.system.service.impl;

import com.aurum.crm.common.core.DataScope;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.system.core.DisplayEnumTranslator;
import com.aurum.crm.module.system.dal.dataobject.SysRoleDO;
import com.aurum.crm.module.system.dal.dataobject.SysRoleDeptDO;
import com.aurum.crm.module.system.dal.dataobject.SysRoleGroupDO;
import com.aurum.crm.module.system.dal.dataobject.SysRoleMenuDO;
import com.aurum.crm.module.system.dal.dataobject.SysUserDO;
import com.aurum.crm.module.system.dal.dataobject.SysUserRoleDO;
import com.aurum.crm.module.system.dal.mysql.SysRoleDeptMapper;
import com.aurum.crm.module.system.dal.mysql.SysRoleGroupMapper;
import com.aurum.crm.module.system.dal.mysql.SysRoleMapper;
import com.aurum.crm.module.system.dal.mysql.SysRoleMenuMapper;
import com.aurum.crm.module.system.dal.mysql.SysUserMapper;
import com.aurum.crm.module.system.dal.mysql.SysUserRoleMapper;
import com.aurum.crm.module.system.model.convert.RoleConvert;
import com.aurum.crm.module.system.model.dto.RolePageReqVO;
import com.aurum.crm.module.system.model.dto.RolePermissionReqVO;
import com.aurum.crm.module.system.model.dto.RoleSaveReqVO;
import com.aurum.crm.module.system.model.dto.RoleUserReqVO;
import com.aurum.crm.module.system.model.vo.RoleRespVO;
import com.aurum.crm.module.system.model.vo.UserRespVO;
import com.aurum.crm.module.system.service.RoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色配置实现（接口文档 3.2）。
 *
 * <p>三张关联表的维护规则：
 * <ul>
 *     <li>{@code sys_user_role} —— 角色人员，分配是<b>追加</b>语义（前端 mock 用 Set 合并），移除才删</li>
 *     <li>{@code sys_role_menu} —— 菜单权限，保存是<b>全量覆盖</b>（先删后插）</li>
 *     <li>{@code sys_role_dept} —— 自定义数据范围，<b>只在 dataScope=自定义 时有意义</b>；
 *         切到别的范围必须清空，否则下次切回来会带着旧的部门集合</li>
 * </ul>
 */
@Slf4j
@Service
public class RoleServiceImpl implements RoleService {

    @Resource
    private SysRoleMapper sysRoleMapper;
    @Resource
    private SysRoleMenuMapper sysRoleMenuMapper;
    @Resource
    private SysRoleGroupMapper sysRoleGroupMapper;
    @Resource
    private SysRoleDeptMapper sysRoleDeptMapper;
    @Resource
    private SysUserRoleMapper sysUserRoleMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private RoleConvert roleConvert;
    @Resource
    private UserVoAssembler userVoAssembler;

    // ========================================================================
    //  分页 / 增删改
    // ========================================================================

    @Override
    public PageResult<RoleRespVO> pageQuery(RolePageReqVO reqVO) {
        LambdaQueryWrapper<SysRoleDO> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(reqVO.getKeyword())) {
            String kw = reqVO.getKeyword().trim();
            wrapper.and(w -> w.like(SysRoleDO::getRoleName, kw).or().like(SysRoleDO::getRoleKey, kw));
        }
        wrapper.orderByAsc(SysRoleDO::getSort).orderByAsc(SysRoleDO::getRoleId);

        Page<SysRoleDO> page = sysRoleMapper.selectPage(reqVO, wrapper);

        // 人数实时统计：一次查出这批角色的关联行，在内存里分组，避免每个角色查一次
        Map<Long, Integer> counts = userCountMap(
                page.getRecords().stream().map(SysRoleDO::getRoleId).collect(Collectors.toList()));

        // 角色组名跨表补：一次查出这批角色涉及的组，避免每行查一次
        Map<Long, String> groupNames = groupNameMap(page.getRecords());

        List<RoleRespVO> list = page.getRecords().stream().map(r -> {
            RoleRespVO vo = roleConvert.toRespVO(r);
            vo.setDataScope(DisplayEnumTranslator.dataScopeToText(r.getDataScope()));
            vo.setUserCount(counts.getOrDefault(r.getRoleId(), 0));
            // 未分组时空串而不是 null —— 列表列直接渲染，null 会显示成 "null"
            // ⚠️ 不能 getOrDefault(null)：groupIds 为空时是 Map.of()（不可变），会 NPE
            vo.setGroupName(r.getGroupId() == null ? "" : groupNames.getOrDefault(r.getGroupId(), ""));
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RoleSaveReqVO reqVO) {
        checkRoleKeyAndNameUnique(reqVO.getRoleKey(), reqVO.getName(), null);

        SysRoleDO role = new SysRoleDO();
        role.setRoleName(reqVO.getName());
        role.setRoleKey(reqVO.getRoleKey().trim());
        role.setDataScope(DisplayEnumTranslator.dataScopeToCode(reqVO.getDataScope()));
        role.setStatus(1);
        role.setSort(0);
        role.setRemark(reqVO.getRemark());
        role.setGroupId(checkGroupExists(reqVO.getGroupId()));
        sysRoleMapper.insert(role);

        saveRoleDepts(role.getRoleId(), role.getDataScope(), reqVO.getDeptIds());
        return role.getRoleId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RoleSaveReqVO reqVO) {
        SysRoleDO exists = getRoleDO(id);
        checkRoleKeyAndNameUnique(reqVO.getRoleKey(), reqVO.getName(), id);

        Integer dataScope = DisplayEnumTranslator.dataScopeToCode(reqVO.getDataScope());

        SysRoleDO update = new SysRoleDO();
        update.setRoleId(id);
        update.setRoleName(reqVO.getName());
        // ⚠️ roleKey 刻意不更新：它是程序里判权限用的字符串，改了会让已有的授权配置失效
        //    （前端编辑态也把它禁用了）。若将来要放开，必须同时迁移 sys_role_menu 之外的所有引用
        update.setDataScope(dataScope);
        update.setRemark(reqVO.getRemark());

        // ⚠️ groupId 单独用 wrapper 的 .set() 来写，因为要支持「清空分组」（置为 null）。
        //    全局 update-strategy=not_null 会把 updateById 里的 null 字段忽略掉，
        //    所以直接 update.setGroupId(null) 是清不掉的。
        //    而 entity 与 wrapper 一起传（entity 非 null）才能保住 MetaObjectHandler
        //    对 update_by / update_time 的自动填充 —— 只传 wrapper.update(null, wrapper)
        //    不会触发自动填充。
        Long groupId = checkGroupExists(reqVO.getGroupId());
        sysRoleMapper.update(update, Wrappers.<SysRoleDO>lambdaUpdate()
                .set(SysRoleDO::getGroupId, groupId)
                .eq(SysRoleDO::getRoleId, id));

        // 数据范围变了，对应的部门集合要跟着重建（切到非自定义时会被清空）
        saveRoleDepts(id, dataScope, reqVO.getDeptIds());
        log.info("[角色修改] roleId={} dataScope={}（{}）", id, dataScope, exists.getRoleKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getRoleDO(id);

        // 有用户在用就不给删 —— 否则这些用户会变成「有账号无角色」，
        // 登录后拿不到任何权限，而管理员看不出是为什么
        if (sysUserRoleMapper.exists(SysUserRoleDO::getRoleId, id)) {
            throw new ServiceException(ErrorCode.ROLE_HAS_USER);
        }

        // 清掉授权关联，避免留下孤儿行
        sysRoleMenuMapper.delete(Wrappers.<SysRoleMenuDO>lambdaQuery().eq(SysRoleMenuDO::getRoleId, id));
        sysRoleDeptMapper.delete(Wrappers.<SysRoleDeptDO>lambdaQuery().eq(SysRoleDeptDO::getRoleId, id));
        sysRoleMapper.deleteById(id);
    }

    // ========================================================================
    //  菜单权限
    // ========================================================================

    @Override
    public List<Long> listPermissionIds(Long roleId) {
        getRoleDO(roleId);
        // ⚠️ 返回【裸数组】而不是 {menuIds:[...]} —— 前端 setCheckedKeys 直接吃数组。
        //    保存方向收的却是对象，两个方向不对称，前端就是这么写的
        return sysRoleMenuMapper.selectList(SysRoleMenuDO::getRoleId, roleId).stream()
                .map(SysRoleMenuDO::getMenuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void savePermissions(Long roleId, RolePermissionReqVO reqVO) {
        getRoleDO(roleId);

        // 全量覆盖：先清空再写。角色授权数量有限，比增量 diff 简单且不会漏删
        sysRoleMenuMapper.delete(Wrappers.<SysRoleMenuDO>lambdaQuery().eq(SysRoleMenuDO::getRoleId, roleId));

        Set<Long> menuIds = new LinkedHashSet<>();
        if (reqVO.getMenuIds() != null) {
            reqVO.getMenuIds().stream().filter(Objects::nonNull).forEach(menuIds::add);
        }
        for (Long menuId : menuIds) {
            SysRoleMenuDO relation = new SysRoleMenuDO();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            sysRoleMenuMapper.insert(relation);
        }
        log.info("[保存权限] roleId={} 写入 {} 条菜单授权", roleId, menuIds.size());
    }

    // ========================================================================
    //  角色人员
    // ========================================================================

    @Override
    public PageResult<UserRespVO> pageUsers(Long roleId, RolePageReqVO reqVO) {
        getRoleDO(roleId);

        Set<Long> userIds = sysUserRoleMapper.selectList(SysUserRoleDO::getRoleId, roleId).stream()
                .map(SysUserRoleDO::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return PageResult.empty(reqVO.getPageNo(), reqVO.getPageSize());
        }

        // ⚠️ 必须写 Wrappers.<SysUserDO>lambdaQuery() 的显式类型参数：
        //    不加的话，链式 .in(...) 会让编译器把 wrapper 的类型推断成 Object，
        //    报「找不到合适的 in 方法」
        LambdaQueryWrapper<SysUserDO> wrapper = Wrappers.<SysUserDO>lambdaQuery()
                .in(SysUserDO::getUserId, userIds);
        if (StringUtils.hasText(reqVO.getKeyword())) {
            String kw = reqVO.getKeyword().trim();
            wrapper.and(w -> w.like(SysUserDO::getRealName, kw).or().like(SysUserDO::getUsername, kw));
        }
        wrapper.orderByDesc(SysUserDO::getCreateTime);

        Page<SysUserDO> page = sysUserMapper.selectPage(reqVO, wrapper);
        List<UserRespVO> list = userVoAssembler.toRespList(page.getRecords());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUsers(Long roleId, RoleUserReqVO reqVO) {
        getRoleDO(roleId);
        if (reqVO.getUserIds() == null || reqVO.getUserIds().isEmpty()) {
            return;
        }

        // ⚠️ 追加语义（前端 mock 用 Set 合并）：已在该角色下的人不重复插入，
        //    否则会撞 sys_user_role 的复合主键 (user_id, role_id)
        Set<Long> existing = sysUserRoleMapper.selectList(SysUserRoleDO::getRoleId, roleId).stream()
                .map(SysUserRoleDO::getUserId).collect(Collectors.toSet());

        Set<Long> toAdd = reqVO.getUserIds().stream()
                .filter(Objects::nonNull)
                .filter(uid -> !existing.contains(uid))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (toAdd.isEmpty()) {
            return;
        }

        // 校验用户存在，避免写进去一个查不到的 userId
        long found = sysUserMapper.selectCount(Wrappers.<SysUserDO>lambdaQuery().in(SysUserDO::getUserId, toAdd));
        if (found != toAdd.size()) {
            throw new ServiceException(ErrorCode.USER_NOT_EXISTS);
        }

        for (Long userId : toAdd) {
            SysUserRoleDO relation = new SysUserRoleDO();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            sysUserRoleMapper.insert(relation);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeUsers(Long roleId, RoleUserReqVO reqVO) {
        getRoleDO(roleId);
        if (reqVO.getUserIds() == null || reqVO.getUserIds().isEmpty()) {
            return;
        }
        sysUserRoleMapper.delete(Wrappers.<SysUserRoleDO>lambdaQuery()
                .eq(SysUserRoleDO::getRoleId, roleId)
                .in(SysUserRoleDO::getUserId, reqVO.getUserIds()));
    }

    // ========================================================================
    //  内部方法
    // ========================================================================

    private SysRoleDO getRoleDO(Long id) {
        SysRoleDO role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new ServiceException(ErrorCode.ROLE_NOT_EXISTS);
        }
        return role;
    }

    /** 角色标识与角色名称都要唯一（标识是登录鉴权用的，重了权限会串） */
    private void checkRoleKeyAndNameUnique(String roleKey, String roleName, Long excludeId) {
        if (StringUtils.hasText(roleKey)) {
            SysRoleDO byKey = sysRoleMapper.selectOne(SysRoleDO::getRoleKey, roleKey.trim());
            if (byKey != null && !byKey.getRoleId().equals(excludeId)) {
                throw new ServiceException(ErrorCode.ROLE_KEY_EXISTS);
            }
        }
        if (StringUtils.hasText(roleName)) {
            SysRoleDO byName = sysRoleMapper.selectOne(SysRoleDO::getRoleName, roleName.trim());
            if (byName != null && !byName.getRoleId().equals(excludeId)) {
                throw new ServiceException(ErrorCode.ROLE_NAME_EXISTS);
            }
        }
    }

    /**
     * 重建角色的「自定义数据范围」部门集合。
     *
     * <p>⚠️ 无论什么范围都先清空：切到非自定义时必须把旧的清掉，
     * 否则将来切回自定义会带着上次的部门集合，而用户以为自己重新选了。
     */
    private void saveRoleDepts(Long roleId, Integer dataScope, List<Long> deptIds) {
        sysRoleDeptMapper.delete(Wrappers.<SysRoleDeptDO>lambdaQuery().eq(SysRoleDeptDO::getRoleId, roleId));

        if (!DataScope.CUSTOM.equals(dataScope) || deptIds == null || deptIds.isEmpty()) {
            return;
        }
        for (Long deptId : deptIds.stream().filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))) {
            SysRoleDeptDO relation = new SysRoleDeptDO();
            relation.setRoleId(roleId);
            relation.setDeptId(deptId);
            sysRoleDeptMapper.insert(relation);
        }
    }

    /**
     * 校验角色组存在，返回可安全写入的 groupId。
     *
     * <p>{@code null} 是合法值（= 未分组），直接放行；非 null 但查不到则报错 ——
     * 否则角色表里会写进一个指向不存在分组的 id，列表按组归类时它既不属于任何组、
     * 又不算「未分组」，界面上会凭空消失。
     */
    private Long checkGroupExists(Long groupId) {
        if (groupId == null) {
            return null;
        }
        if (sysRoleGroupMapper.selectById(groupId) == null) {
            throw new ServiceException(ErrorCode.ROLE_GROUP_NOT_EXISTS);
        }
        return groupId;
    }

    /** 批量取角色组名（一次查完，避免每行查一次） */
    private Map<Long, String> groupNameMap(List<SysRoleDO> roles) {
        Set<Long> groupIds = roles.stream().map(SysRoleDO::getGroupId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        return sysRoleGroupMapper.selectList(SysRoleGroupDO::getGroupId, groupIds).stream()
                .collect(Collectors.toMap(SysRoleGroupDO::getGroupId, SysRoleGroupDO::getGroupName, (a, b) -> a));
    }

    /** 批量统计每个角色下有多少人 */
    private Map<Long, Integer> userCountMap(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Map.of();
        }
        List<SysUserRoleDO> relations = sysUserRoleMapper.selectList(SysUserRoleDO::getRoleId, roleIds);
        if (relations.isEmpty()) {
            return Map.of();
        }
        return relations.stream()
                .filter(r -> r.getRoleId() != null)
                .collect(Collectors.toMap(SysUserRoleDO::getRoleId, r -> 1, Integer::sum));
    }
}
