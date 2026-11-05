package com.aurum.crm.module.system.dal.dataobject;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门 DO，对应表 {@code sys_dept}。
 *
 * <p>注意 {@code status} 取值是 <b>0 停用 / 1 正常</b>，
 * 与 {@link SysUserDO#getStatus()}（1 正常 / 2 禁用 / 3 离职）的语义不同，别照抄。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDeptDO extends BaseDO {

    /** 部门 ID */
    @TableId
    private Long deptId;

    /** 父部门 ID（0 为顶级） */
    private Long parentId;

    /**
     * 祖级列表，逗号分隔（如 {@code 0,1}）。
     *
     * <p>查「本部门及以下」时靠它做前缀匹配，避免递归查询。
     */
    private String ancestors;

    /** 部门名称 */
    private String deptName;

    /**
     * 类型：1 集团 / 2 部门 / 3 组。
     *
     * <p>前端组织架构页按它给节点上不同的徽标底色。
     * ⚠️ 前端传的 value 是**英文字符串** {@code group/dept/team}（见 {@code options.js} 的
     * {@code DEPT_TYPE_OPTIONS}），不是这几个数字 —— 靠
     * {@code DisplayEnumTranslator} 在两边转换。
     */
    private Integer type;

    /** 部门负责人用户 ID */
    private Long leaderUserId;

    /** 部门编码 */
    private String deptCode;

    /** 联系电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 显示排序 */
    private Integer sort;

    /** 状态：0 停用，1 正常 */
    private Integer status;
}
