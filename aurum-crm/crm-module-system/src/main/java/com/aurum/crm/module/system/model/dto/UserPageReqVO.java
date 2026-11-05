package com.aurum.crm.module.system.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 人员分页查询（接口文档 3.1）。
 *
 * <p>前端传的字段：{@code keyword} / {@code status} / {@code deptId} + 分页参数。
 * 「筛选胶囊」（全部/已激活/已禁用/离职）在前端被翻译成数字 {@code status} 后传过来，
 * 所以这里的 {@code status} 是<b>数字</b>（1/2/3），不是表单里那套中文口径。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "人员分页查询")
public class UserPageReqVO extends PageParam {

    @Schema(description = "姓名或账号，模糊搜索")
    private String keyword;

    /**
     * 账号状态：1 正常 / 2 禁用 / 3 离职。
     *
     * <p>注意与新增/修改表单里的 {@code workStatus} / {@code accountStatus} 区分 ——
     * 那两处是中文展示口径，这里查询用的是码值。
     */
    @Schema(description = "状态：1 正常 / 2 禁用 / 3 离职")
    private Integer status;

    /** 所属部门 ID。⚠️ 语义是「含子部门」（接口文档 3.1） */
    @Schema(description = "部门ID（含子部门）")
    private Long deptId;

    /**
     * 只筛出「还没有任何角色」的账号。
     *
     * <p>注册出来的账号就落在这里（{@code status=2} 且 {@code sys_user_role} 无行），
     * 管理员用它集中找出待处理的新注册申请。见接口文档 2.4 / 3.1。
     */
    @Schema(description = "只看未分配角色的账号（注册后的待处理申请）")
    private Boolean noRole;
}
