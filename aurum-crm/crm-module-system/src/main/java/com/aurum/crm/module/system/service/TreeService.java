package com.aurum.crm.module.system.service;

import com.aurum.crm.module.system.model.vo.DeptTreeNodeVO;
import com.aurum.crm.module.system.model.vo.MenuTreeVO;

import java.util.List;

/** 部门树与菜单树。两棵都是表单的前置依赖，不是可选功能 */
public interface TreeService {

    /** 部门树。节点字段是 id / name / children（前端 DeptTreeSelect 约定） */
    List<DeptTreeNodeVO> deptTree();

    /** 菜单树（全量，不做角色过滤）。供角色配置页勾选权限 */
    List<MenuTreeVO> menuTree();
}
