package com.aurum.crm.module.crm.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_user_role")
public class SysUserRoleMiniDO {
    private Long userId;
    private Long roleId;
}
