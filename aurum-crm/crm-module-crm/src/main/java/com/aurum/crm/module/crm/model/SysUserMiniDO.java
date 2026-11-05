package com.aurum.crm.module.crm.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 系统用户精简投影，只取客户列表「负责人」列展示所需的字段。
 *
 * <p>放在 crm 模块里仅用于跨表补名，不进入 system 模块的 CRUD 链路。
 */
@Data
@TableName("sys_user")
public class SysUserMiniDO implements Serializable {

    /** 用户ID */
    @TableId
    private Long userId;

    /** 真实姓名 */
    private String realName;

    /** 所属部门ID */
    private Long deptId;

    /** 直属上级用户ID（下属遍历用，见 {@code LeadServiceImpl#listSubordinateUserIds}） */
    private Long superiorId;

}
