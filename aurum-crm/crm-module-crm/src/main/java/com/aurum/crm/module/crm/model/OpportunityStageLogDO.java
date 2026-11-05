package com.aurum.crm.module.crm.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商机阶段变更记录 DO，对应表 {@code crm_opportunity_stage_log}。
 *
 * <p><b>为什么不能继承 {@code BaseDO}</b>：{@code BaseDO} 带着
 * {@code @TableLogic} 的 {@code delFlag}，而本表是**只追加流水表**
 * （《02-建表文档》末尾的约定：只追加流水表只需 {@code create_time}），
 * 库里根本没有 {@code del_flag} / {@code update_time} / {@code create_by} 三列。
 * 继承了会让 MyBatis-Plus 生成的每一条 SELECT 都追加 {@code del_flag = 0}，
 * 直接报 {@code Unknown column 'del_flag'}。这里按 {@code OwnerChangeLogDO} 的写法手写审计字段。
 *
 * <p><b>只追加，不更新不删除</b>：阶段变更历史是详情页「阶段变更历史」Tab 的数据来源，
 * 只增不改才具备审计意义。
 */
@Data
@TableName("crm_opportunity_stage_log")
public class OpportunityStageLogDO implements Serializable {

    /** 日志ID */
    @TableId
    private Long logId;

    /** 商机ID */
    private Long opportunityId;

    /** 原阶段（首次落库时为 NULL） */
    private Integer fromStage;

    /** 新阶段 */
    private Integer toStage;

    /**
     * 变更后赢单概率(%)。
     *
     * <p><b>恒为 0</b>：{@code crm_opportunity.win_rate} 已被改名成 {@code discount_rate}
     * （整单折扣），赢单概率这个业务概念不再存在；但本列在建表脚本里是
     * {@code NOT NULL DEFAULT 0.00}，所以写入时**必须给一个值**，这里统一给 0。
     *
     * <p>以后若这个列被删掉，要改的只有三处：本字段、{@code OpportunityServiceImpl#writeStageLog}
     * 的 {@code setWinRate}、以及 {@code sql/crm.sql}（建表 + 种子 INSERT）。
     */
    private BigDecimal winRate;

    /** 变更原因（前端的「变更说明」就是写进这一列 → 对外字段名 {@code remark}） */
    private String changeReason;

    /** 来源（1人工 2AI）。本轮只写 1，AI 预测落地后写 2 */
    private Integer source;

    /** AI置信度，AI 预测落地前恒为 NULL */
    private BigDecimal aiConfidence;

    /** 操作人ID */
    private Long operatorId;

    /** 变更时间（秒精度，由 {@code DefaultDBFieldHandler} 自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
