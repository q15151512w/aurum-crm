package com.aurum.crm.module.system.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.model.dto.RolePageReqVO;
import com.aurum.crm.module.system.model.dto.RolePermissionReqVO;
import com.aurum.crm.module.system.model.dto.RoleSaveReqVO;
import com.aurum.crm.module.system.model.dto.RoleUserReqVO;
import com.aurum.crm.module.system.model.vo.RoleRespVO;
import com.aurum.crm.module.system.model.vo.UserRespVO;

import java.util.List;

/**
 * 角色配置（接口文档 3.2）。
 *
 * <p>注意两个不对称的契约（前端就是这么写的，别统一）：
 * <ul>
 *     <li>{@link #listPermissionIds} 返回<b>裸数组</b>，{@link #savePermissions} 提交<b>对象</b></li>
 *     <li>{@link #removeUsers} 用 <b>DELETE + request body</b></li>
 * </ul>
 */
public interface RoleService {

    PageResult<RoleRespVO> pageQuery(RolePageReqVO reqVO);

    Long create(RoleSaveReqVO reqVO);

    void update(Long id, RoleSaveReqVO reqVO);

    /** 删除角色。该角色下还有用户时拒绝（1103） */
    void delete(Long id);

    /** 角色的菜单权限 ID 集合。返回裸数组 */
    List<Long> listPermissionIds(Long roleId);

    /** 保存角色的菜单权限 */
    void savePermissions(Long roleId, RolePermissionReqVO reqVO);

    /** 角色下的人员分页 */
    PageResult<UserRespVO> pageUsers(Long roleId, RolePageReqVO reqVO);

    /** 追加人员到角色 */
    void assignUsers(Long roleId, RoleUserReqVO reqVO);

    /** 从角色移除人员 */
    void removeUsers(Long roleId, RoleUserReqVO reqVO);
}
