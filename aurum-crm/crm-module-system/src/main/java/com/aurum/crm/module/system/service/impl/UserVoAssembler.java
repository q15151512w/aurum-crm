package com.aurum.crm.module.system.service.impl;

import com.aurum.crm.module.system.core.DisplayEnumTranslator;
import com.aurum.crm.module.system.dal.dataobject.SysDeptDO;
import com.aurum.crm.module.system.dal.dataobject.SysRoleDO;
import com.aurum.crm.module.system.dal.dataobject.SysUserDO;
import com.aurum.crm.module.system.dal.dataobject.SysUserRoleDO;
import com.aurum.crm.module.system.dal.mysql.SysDeptMapper;
import com.aurum.crm.module.system.dal.mysql.SysRoleMapper;
import com.aurum.crm.module.system.dal.mysql.SysUserMapper;
import com.aurum.crm.module.system.dal.mysql.SysUserRoleMapper;
import com.aurum.crm.module.system.model.convert.UserConvert;
import com.aurum.crm.module.system.model.vo.UserRespVO;
import com.aurum.crm.module.system.model.vo.UserSimpleRespVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 把 {@code SysUserDO} 列表装配成「展示口径」的 {@link UserRespVO}。
 *
 * <p><b>为什么单独抽出来</b>：人员列表（{@code /system/user}）和角色人员列表
 * （{@code /system/role/{id}/users}）返回的是同一种 VO，都要做三件事：
 * <ol>
 *     <li>中文转换（gender / workStatus / accountStatus）</li>
 *     <li>跨表补名（deptName / superiorName / roleName）</li>
 *     <li>批量查（不能每行查一次，那会变成 N+1）</li>
 * </ol>
 * 抄两遍的话，将来改口径必然漏一处。
 */
@Component
public class UserVoAssembler {

    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysDeptMapper sysDeptMapper;
    @Resource
    private SysRoleMapper sysRoleMapper;
    @Resource
    private SysUserRoleMapper sysUserRoleMapper;
    @Resource
    private UserConvert userConvert;

    /** 批量装配。所有跨表查询都是批量一次，不做逐行查询 */
    public List<UserRespVO> toRespList(List<SysUserDO> users) {
        if (users == null || users.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, String> deptNames = deptNameMap(users);
        Map<Long, String> superiorNames = superiorNameMap(users);
        Map<Long, String> roleNames = roleNameMap(users);

        return users.stream().map(u -> {
            UserRespVO vo = userConvert.toRespVO(u);
            // 中文转换：库里是 TINYINT，前端要中文
            vo.setGender(DisplayEnumTranslator.genderToText(u.getGender()));
            vo.setWorkStatus(DisplayEnumTranslator.workStatusToText(u.getStatus()));
            vo.setAccountStatus(DisplayEnumTranslator.accountStatusToText(u.getStatus()));
            vo.setDeptName(lookup(deptNames, u.getDeptId(), null));
            // 未设置上级时给空串而不是 null —— 前端列直接渲染，null 会显示成 "null"
            vo.setSuperiorName(lookup(superiorNames, u.getSuperiorId(), ""));
            vo.setRoleName(lookup(roleNames, u.getUserId(), ""));
            return vo;
        }).collect(Collectors.toList());
    }

    /** 单个装配（详情用） */
    public UserRespVO toResp(SysUserDO user) {
        return toRespList(List.of(user)).get(0);
    }

    /**
     * 装配「选择器专用」的精简 VO。
     *
     * <p>只要 4 个展示字段（{@code name/phone/deptName/username}），
     * 不需要角色名、上级名那些 —— 选择器的列就这么几列。
     */
    public List<UserSimpleRespVO> toSimpleList(List<SysUserDO> users) {
        if (users == null || users.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, String> deptNames = deptNameMap(users);
        return users.stream().map(u -> {
            UserSimpleRespVO vo = userConvert.toSimpleVO(u);
            vo.setDeptName(lookup(deptNames, u.getDeptId(), null));
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 查 Map，**key 为 null 时不去查**、直接返回兜底值。
     *
     * <p>⚠️ <b>不能写成 {@code map.getOrDefault(key, fallback)}</b> —— 这是个踩过的坑：
     * 那几个 Map 在「一个都没查到」时返回的是 {@code Map.of()}（<b>不可变</b>），
     * 而 {@code ImmutableCollections.MapN.getOrDefault(null, x)} 内部会先
     * {@code Objects.requireNonNull(key)} 直接抛 NPE；换成一个普通的 HashMap 则不会。
     *
     * <p>后果是「大部分时候正常、特定数据才 500」：比如
     * {@code /system/role/1/users}（只有 admin 一人，他的 superior_id 是 NULL）必炸，
     * 而 {@code /system/user} 列表里混着几个有上级的人就不炸 —— 极难排查。
     *
     * <p>所以这里统一先判 key，把「Map 的实现类」这个不确定因素彻底排除掉。
     */
    private static <V> V lookup(Map<Long, V> map, Long key, V fallback) {
        return key == null ? fallback : map.getOrDefault(key, fallback);
    }

    private Map<Long, String> deptNameMap(List<SysUserDO> users) {
        Set<Long> deptIds = users.stream().map(SysUserDO::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (deptIds.isEmpty()) {
            return Map.of();
        }
        return sysDeptMapper.selectList(SysDeptDO::getDeptId, deptIds).stream()
                .collect(Collectors.toMap(SysDeptDO::getDeptId, SysDeptDO::getDeptName, (a, b) -> a));
    }

    private Map<Long, String> superiorNameMap(List<SysUserDO> users) {
        Set<Long> superiorIds = users.stream().map(SysUserDO::getSuperiorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (superiorIds.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectList(SysUserDO::getUserId, superiorIds).stream()
                .collect(Collectors.toMap(SysUserDO::getUserId, SysUserDO::getRealName, (a, b) -> a));
    }

    /**
     * 每个用户的角色名。
     *
     * <p>一个用户可能有多个角色，列表的「角色」列是单值展示，这里取**第一个**。
     */
    private Map<Long, String> roleNameMap(List<SysUserDO> users) {
        List<Long> userIds = users.stream().map(SysUserDO::getUserId).collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<SysUserRoleDO> relations = sysUserRoleMapper.selectList(SysUserRoleDO::getUserId, userIds);
        if (relations.isEmpty()) {
            return Map.of();
        }
        Set<Long> roleIds = relations.stream().map(SysUserRoleDO::getRoleId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> nameById = sysRoleMapper.selectList(SysRoleDO::getRoleId, roleIds).stream()
                .collect(Collectors.toMap(SysRoleDO::getRoleId, SysRoleDO::getRoleName, (a, b) -> a));

        return relations.stream()
                .filter(r -> nameById.containsKey(r.getRoleId()))
                .collect(Collectors.toMap(SysUserRoleDO::getUserId,
                        r -> nameById.get(r.getRoleId()),
                        (a, b) -> a));
    }
}
