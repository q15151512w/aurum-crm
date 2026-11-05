package com.aurum.crm.module.system.service.impl;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.system.dal.dataobject.SysRoleDO;
import com.aurum.crm.module.system.dal.dataobject.SysRoleGroupDO;
import com.aurum.crm.module.system.dal.mysql.SysRoleGroupMapper;
import com.aurum.crm.module.system.dal.mysql.SysRoleMapper;
import com.aurum.crm.module.system.model.dto.RoleGroupPageReqVO;
import com.aurum.crm.module.system.model.dto.RoleGroupSaveReqVO;
import com.aurum.crm.module.system.model.vo.RoleGroupRespVO;
import com.aurum.crm.module.system.service.RoleGroupService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 角色组实现（接口文档 3.2）。
 *
 * <p><b>定位：纯分组标签</b>。角色组不参与权限判定，它的唯一作用是让角色能按组归类，
 * 所以这里没有复杂规则，只有两条要守住：
 * <ol>
 *     <li>组名唯一（存活行内）</li>
 *     <li><b>删组时清掉成员的 group_id</b> —— 否则角色表里留下指向已删组的孤儿引用</li>
 * </ol>
 */
@Slf4j
@Service
public class RoleGroupServiceImpl implements RoleGroupService {

    @Resource
    private SysRoleGroupMapper sysRoleGroupMapper;
    @Resource
    private SysRoleMapper sysRoleMapper;

    @Override
    public PageResult<RoleGroupRespVO> pageQuery(RoleGroupPageReqVO reqVO) {
        LambdaQueryWrapper<SysRoleGroupDO> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(reqVO.getKeyword())) {
            wrapper.like(SysRoleGroupDO::getGroupName, reqVO.getKeyword().trim());
        }
        wrapper.orderByAsc(SysRoleGroupDO::getSort).orderByAsc(SysRoleGroupDO::getGroupId);

        Page<SysRoleGroupDO> page = sysRoleGroupMapper.selectPage(reqVO, wrapper);
        Map<Long, Integer> counts = roleCountMap(
                page.getRecords().stream().map(SysRoleGroupDO::getGroupId).collect(Collectors.toList()));

        List<RoleGroupRespVO> list = page.getRecords().stream().map(g -> {
            RoleGroupRespVO vo = new RoleGroupRespVO();
            vo.setId(g.getGroupId());
            // 展示口径：name ← group_name（前端列表的 prop 是 name）
            vo.setName(g.getGroupName());
            vo.setRoleCount(counts.getOrDefault(g.getGroupId(), 0));
            vo.setRemark(g.getRemark());
            vo.setCreateTime(g.getCreateTime());
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RoleGroupSaveReqVO reqVO) {
        // 组名唯一：走 ORM 查重，@TableLogic 自动带 del_flag=0，
        // 与唯一键 uk_group_name(group_name, is_active)「只约束存活行」的语义一致
        if (sysRoleGroupMapper.exists(SysRoleGroupDO::getGroupName, reqVO.getName().trim())) {
            throw new ServiceException(ErrorCode.ROLE_GROUP_NAME_EXISTS);
        }

        SysRoleGroupDO group = new SysRoleGroupDO();
        group.setGroupName(reqVO.getName().trim());
        group.setRemark(reqVO.getRemark());
        group.setSort(0);
        sysRoleGroupMapper.insert(group);
        return group.getGroupId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (sysRoleGroupMapper.selectById(id) == null) {
            throw new ServiceException(ErrorCode.ROLE_GROUP_NOT_EXISTS);
        }

        // ⚠️ 先把组内角色的 group_id 置回 NULL 再删组。
        //    不置的话角色表里会留下指向已删组的孤儿 group_id —— 列表按组归类时
        //    那些角色既不属于任何现存组、又不算「未分组」，界面上会凭空消失。
        //    这里用 LambdaUpdateWrapper 显式 set(null)：全局 update-strategy=not_null
        //    会忽略 null 字段，用 updateById 是清不掉的。
        int cleared = sysRoleMapper.update(null, Wrappers.<SysRoleDO>lambdaUpdate()
                .set(SysRoleDO::getGroupId, null)
                .eq(SysRoleDO::getGroupId, id));

        sysRoleGroupMapper.deleteById(id);
        log.info("[删除角色组] groupId={} 已把 {} 个角色置为未分组", id, cleared);
    }

    /** 批量统计每个组下有多少角色（一次查出来在内存分组，避免每组查一次） */
    private Map<Long, Integer> roleCountMap(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Map.of();
        }
        return sysRoleMapper.selectList(Wrappers.<SysRoleDO>lambdaQuery()
                        .in(SysRoleDO::getGroupId, groupIds))
                .stream()
                .filter(r -> Objects.nonNull(r.getGroupId()))
                .collect(Collectors.toMap(SysRoleDO::getGroupId, r -> 1, Integer::sum));
    }
}
