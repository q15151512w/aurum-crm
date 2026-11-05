package com.aurum.crm.module.crm.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 归属流转记录 DO，对应表 {@code crm_owner_change_log}。
 *
 * <p><b>只追加，不更新不删除</b>——设计上就是流水账，
 * 客户进入/离开公海、分配、领取都会写入一条记录（客户详情「操作记录」Tab 的数据来源）。
 */
@Data
@TableName("crm_owner_change_log")
public class OwnerChangeLogDO implements Serializable {

    /** 主键 */
    @TableId
    private Long logId;

    /** 业务类型（1线索 2客户） */
    private Integer bizType;

    /** 业务ID */
    private Long bizId;

    /** 业务名称快照 */
    private String bizName;

    /** 动作（1分配 2领取 3放入公海 4规则自动回收 5管理员改派 6线索转化） */
    private Integer action;

    /** 原归属人（NULL=原在公海） */
    private Long fromOwnerId;

    /** 新归属人（NULL=进入公海） */
    private Long toOwnerId;

    /** 原因编码（字典 crm_pool_reason） */
    private Integer reasonCode;

    /** 原因备注 */
    private String reasonRemark;

    /** 操作人ID（自动回收时为NULL） */
    private Long operatorId;

    /** 操作时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
