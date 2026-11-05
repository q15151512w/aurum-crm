package com.aurum.crm.module.system.service;

import com.aurum.crm.module.system.model.dto.DeptSaveReqVO;

/**
 * 部门服务（《05-接口文档》3.3）。
 *
 * <p>{@code sys_dept} 是三级树（集团 → 部门 → 组），写操作有三条硬约束：
 * <ol>
 *     <li><b>父部门必须存在</b>（否则树的 {@code ancestors} 会挂空）；父部门不能是自己</li>
 *     <li><b>删除前挡子部门与用户</b>（{@code 1202} / {@code 1203}）——避免产生孤儿</li>
 *     <li><b>新增/修改时重算 {@code ancestors}</b>（服务端计算，前端不传）</li>
 * </ol>
 */
public interface DeptService {

    /** 新增，返回新部门 ID */
    Long create(DeptSaveReqVO reqVO);

    /** 修改。未传的字段保持原值（全局 {@code update-strategy: not_null}） */
    void update(Long id, DeptSaveReqVO reqVO);

    /** 逻辑删除。有子部门或用户时分别抛 {@code 1202} / {@code 1203} */
    void delete(Long id);
}
