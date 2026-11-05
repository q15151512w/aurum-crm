package com.aurum.crm.module.system.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.model.dto.RoleGroupPageReqVO;
import com.aurum.crm.module.system.model.dto.RoleGroupSaveReqVO;
import com.aurum.crm.module.system.model.vo.RoleGroupRespVO;

/**
 * 角色组（接口文档 3.2）。
 *
 * <p>前端只有「列表 / 新增 / 删除」三个动作 —— 没有编辑，所以这里也没有 update。
 */
public interface RoleGroupService {

    PageResult<RoleGroupRespVO> pageQuery(RoleGroupPageReqVO reqVO);

    Long create(RoleGroupSaveReqVO reqVO);

    /** 删除角色组。组内还有角色的，会先把它们的 group_id 置回 NULL */
    void delete(Long id);
}
