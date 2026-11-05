package com.aurum.crm.module.bpm.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 用户-角色关联精简投影（审批人类型 2「角色」解析用），只读 {@code sys_user_role}。 */
@Data
@TableName("sys_user_role")
public class BpmUserRoleDO {
    private Long userId;
    private Long roleId;
}
