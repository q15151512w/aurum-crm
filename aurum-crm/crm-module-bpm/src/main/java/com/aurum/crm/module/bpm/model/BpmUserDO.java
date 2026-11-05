package com.aurum.crm.module.bpm.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 系统用户精简投影（bpm 模块内部跨表读），仅取审批人解析所需字段。
 *
 * <p>只读 {@code sys_user}，不进入 system 模块的 CRUD 链路。审批人解析用
 * {@code superiorId}（直属上级）与 {@code realName}（补名）。
 */
@Data
@TableName("sys_user")
public class BpmUserDO {
    @TableId
    private Long userId;
    private String realName;
    private Long deptId;
    /** 直属上级用户 ID（审批人类型 4「直属上级」解析用） */
    private Long superiorId;
}
