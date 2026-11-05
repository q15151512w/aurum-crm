package com.aurum.crm.module.crm.mapper;


import com.aurum.crm.module.crm.model.SysUserMiniDO;
import com.aurum.crm.module.crm.model.vo.SalesOwnerRespVO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统用户精简投影 Mapper，用于客户列表「负责人」列补名与分配时取部门。
 */
@Mapper
public interface SysUserMiniMapper extends BaseMapperX<SysUserMiniDO> {

    /**
     * 查所有在职的销售人员（拥有 {@code sales_director} / {@code sales} 角色），
     * 作为客户新增/修改、公海分配的「负责人」下拉选项。
     *
     * <p>按 role_key 过滤而不是硬编码 role_id —— 种子数据里是 2/3，但换环境
     * 重新初始化后 ID 可能对不上；role_key 是角色唯一键，语义稳定。
     */
    @Select("SELECT DISTINCT u.user_id, u.real_name, u.phone, d.dept_name " +
            "FROM sys_user u " +
            "JOIN sys_user_role ur ON u.user_id = ur.user_id " +
            "JOIN sys_role r ON ur.role_id = r.role_id " +
            "    AND r.role_key IN ('sales_director', 'sales') AND r.del_flag = 0 " +
            "LEFT JOIN sys_dept d ON u.dept_id = d.dept_id " +
            "WHERE u.status = 1 AND u.del_flag = 0 " +
            // DISTINCT 下 ORDER BY 只能用 SELECT 列表里的列（ONLY_FULL_GROUP_BY）
            "ORDER BY d.dept_name, u.user_id")
    List<SalesOwnerRespVO> selectSalesOwnerOptions();
}
