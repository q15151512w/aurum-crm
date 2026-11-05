package com.aurum.crm.module.system.service.impl;

import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.system.dal.dataobject.SysDeptDO;
import com.aurum.crm.module.system.dal.dataobject.SysUserDO;
import com.aurum.crm.module.system.dal.mysql.SysDeptMapper;
import com.aurum.crm.module.system.dal.mysql.SysUserMapper;
import com.aurum.crm.module.system.model.dto.DeptSaveReqVO;
import com.aurum.crm.module.system.service.DeptService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 部门实现（《05-接口文档》3.3）。
 *
 * <p><b>ancestors 由服务端维护</b>：新增/改父部门时按「父的 ancestors + 父 id」计算，
 * 前端只传 {@code parentId}，不碰这个字段 —— 与「本部门及以下」按前缀匹配查询的
 * 数据权限语义保持一致。
 *
 * <p><b>删除守卫</b>：有子部门抛 {@code 1202}，有用户抛 {@code 1203}。用户维度按
 * <b>在职（status ≠ 离职）</b>判断——离职用户不算占用部门（他们已不在编）。
 */
@Slf4j
@Service
public class DeptServiceImpl implements DeptService {

    /** 顶级父 ID（与 {@code AuthConstants.MENU_ROOT_PARENT_ID} 语义一致） */
    private static final Long ROOT_PARENT_ID = 0L;

    @Resource
    private SysDeptMapper sysDeptMapper;
    @Resource
    private SysUserMapper sysUserMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(DeptSaveReqVO reqVO) {
        Long parentId = reqVO.getParentId() == null ? ROOT_PARENT_ID : reqVO.getParentId();
        String ancestors = resolveAncestors(parentId);

        SysDeptDO dept = new SysDeptDO();
        dept.setParentId(parentId);
        dept.setAncestors(ancestors);
        dept.setDeptName(reqVO.getDeptName().trim());
        dept.setType(reqVO.getType() == null ? 2 : reqVO.getType());
        dept.setLeaderUserId(reqVO.getLeaderUserId());
        dept.setDeptCode(reqVO.getDeptCode());
        dept.setPhone(reqVO.getPhone());
        dept.setEmail(reqVO.getEmail());
        dept.setSort(reqVO.getSort() == null ? 0 : reqVO.getSort());
        dept.setStatus(reqVO.getStatus() == null ? 1 : reqVO.getStatus());

        sysDeptMapper.insert(dept);
        log.info("[部门] 新增 deptId={} 名称={} 父={}", dept.getDeptId(), dept.getDeptName(), parentId);
        return dept.getDeptId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DeptSaveReqVO reqVO) {
        SysDeptDO existing = sysDeptMapper.selectById(id);
        if (existing == null) {
            throw new ServiceException(ErrorCode.DEPT_NOT_EXISTS);
        }

        SysDeptDO update = new SysDeptDO();
        update.setDeptId(id);
        update.setDeptName(reqVO.getDeptName().trim());
        // type/status/sort 等未传字段走全局 update-strategy=not_null（传 null 不改）
        update.setType(reqVO.getType());
        update.setLeaderUserId(reqVO.getLeaderUserId());
        // ⚠️ 空串要当作「没传」：全局 not_null 只跳过 null，空串会被真的写进去
        update.setDeptCode(StringUtils.hasText(reqVO.getDeptCode()) ? reqVO.getDeptCode() : null);
        update.setPhone(StringUtils.hasText(reqVO.getPhone()) ? reqVO.getPhone() : null);
        update.setEmail(StringUtils.hasText(reqVO.getEmail()) ? reqVO.getEmail() : null);
        update.setSort(reqVO.getSort());
        update.setStatus(reqVO.getStatus());

        // 改了父部门才重算 ancestors；没传 parentId（null）视为不改
        if (reqVO.getParentId() != null && !reqVO.getParentId().equals(existing.getParentId())) {
            update.setParentId(reqVO.getParentId());
            update.setAncestors(resolveAncestors(reqVO.getParentId()));
        }

        sysDeptMapper.updateById(update);
        log.info("[部门] 修改 deptId={} 名称={}", id, update.getDeptName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysDeptDO dept = sysDeptMapper.selectById(id);
        if (dept == null) {
            throw new ServiceException(ErrorCode.DEPT_NOT_EXISTS);
        }

        // 挡子部门：任一存活子部门的 parent_id = id
        if (sysDeptMapper.exists(SysDeptDO::getParentId, id)) {
            throw new ServiceException(ErrorCode.DEPT_HAS_CHILD);
        }
        // 挡用户：部门下还有用户（离职 status=3 除外，见类注释）
        if (sysUserMapper.exists(SysUserDO::getDeptId, id)) {
            Long activeUsers = sysUserMapper.selectCount(Wrappers.<SysUserDO>lambdaQuery()
                    .eq(SysUserDO::getDeptId, id)
                    .ne(SysUserDO::getStatus, 3));
            if (activeUsers != null && activeUsers > 0) {
                throw new ServiceException(ErrorCode.DEPT_HAS_USER);
            }
        }

        // 逻辑删除（BaseDO 的 @TableLogic 把 DELETE 转成 UPDATE del_flag = 1）
        int affected = sysDeptMapper.deleteById(id);
        if (affected == 0) {
            throw new ServiceException(ErrorCode.DEPT_NOT_EXISTS);
        }
        log.info("[部门] 删除 deptId={} 名称={}", id, dept.getDeptName());
    }

    /** 计算 ancestors：父为顶级时 = "0"，否则 = 父.ancestors + "," + 父 id */
    private String resolveAncestors(Long parentId) {
        if (parentId == null || ROOT_PARENT_ID.equals(parentId)) {
            return String.valueOf(ROOT_PARENT_ID);
        }
        SysDeptDO parent = sysDeptMapper.selectById(parentId);
        if (parent == null) {
            throw new ServiceException(ErrorCode.DEPT_NOT_EXISTS);
        }
        String parentAncestors = StringUtils.hasText(parent.getAncestors())
                ? parent.getAncestors()
                : String.valueOf(ROOT_PARENT_ID);
        return parentAncestors + "," + parentId;
    }
}
