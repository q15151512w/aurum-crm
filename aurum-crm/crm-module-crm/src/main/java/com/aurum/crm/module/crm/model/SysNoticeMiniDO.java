package com.aurum.crm.module.crm.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息通知精简投影，供首页看板的「消息」与「公告 | 通知」两个区块使用。
 *
 * <p>与 {@link FinPaymentMiniDO} 同一套定位（不继承 BaseDO、只声明用得到的列）。
 *
 * <h3>⚠️ 这个表不在数据权限白名单里，可见性必须自己限定</h3>
 *
 * <p>{@code sys_notice} 不在 {@code DataPermissionInnerInterceptor} 的
 * {@code SCOPED_TABLES} 白名单中 —— 这是**对的**：消息的可见性不由
 * 「负责人 / 部门」决定，而由 {@code receiver_id}（接收人）决定。
 * 所以查这个表的**每一条语句都必须显式带上 {@code receiver_id = 当前登录人}**，
 * 否则会把别人的消息读出来。
 *
 * <p>{@code notice_type} 的取值：1 审批提醒 / 2 公海回收提醒 / 3 系统公告 / 4 任务提醒。
 * 看板的「公告 | 通知」区块要的是 2/3/4（不含审批提醒，那个走「消息」区块）。
 */
@Data
@TableName("sys_notice")
public class SysNoticeMiniDO implements Serializable {

    /** 通知ID */
    @TableId
    private Long noticeId;

    /** 标题 */
    private String title;

    /** 通知类型（1审批提醒 2公海回收提醒 3系统公告 4任务提醒） */
    private Integer noticeType;

    /** 关联业务类型 */
    private String bizType;

    /** 关联业务ID */
    private Long bizId;

    /** 接收人ID。⚠️ 每个查询都必须用它限定，否则读到别人的消息 */
    private Long receiverId;

    /** 是否已读（0未读 1已读） */
    private Integer isRead;

    /** 发送时间（列表按它倒序） */
    private LocalDateTime sendTime;

    /** 删除标志。⚠️ 不继承 BaseDO 就没有 @TableLogic，查询必须自己带上它 */
    private Integer delFlag;
}
