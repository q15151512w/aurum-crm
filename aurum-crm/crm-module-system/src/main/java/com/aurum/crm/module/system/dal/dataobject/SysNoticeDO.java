package com.aurum.crm.module.system.dal.dataobject;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 消息通知 DO，对应表 {@code sys_notice}（《02-建表文档》附录 A 第 13 张表）。
 *
 * <p><b>本类是 {@code sys_notice} 的写入口</b>（system 模块的「公告/通知管理端」）。
 * {@code crm-module-crm} 里的 {@code SysNoticeMiniDO} 只是只读投影（看板「消息」「公告|通知」用）。
 *
 * <p><b>可见性口径（重要）</b>：{@code sys_notice} 不在数据权限白名单里（它对 —— 消息的
 * 可见性由 {@code receiver_id} 决定，不由「负责人/部门」决定），所以查这张表的每条语句
 * 都必须显式带接收人条件。约定：
 * <ul>
 *     <li>{@code receiver_id = 具体用户} → 定向通知（通知 / 审批提醒 / 任务提醒）</li>
 *     <li>{@code receiver_id = NULL} → <b>全员公告</b>（系统公告广播给所有人）</li>
 * </ul>
 * 「我的消息」查询条件因此是 {@code receiver_id = 我 OR receiver_id IS NULL}。
 *
 * <p><b>两个容易写错的地方</b>（与 {@link SysUnitDO} 同款坑）：
 * <ol>
 *     <li>{@code @TableId} 不要加 type —— 全局已是 {@code ASSIGN_ID}（雪花）</li>
 *     <li>不要声明 {@code isActive} —— 那是数据库生成列，只读</li>
 * </ol>
 *
 * <p>{@code notice_type}：1 审批提醒 / 2 公海回收提醒 / 3 系统公告 / 4 任务提醒。
 * 管理端（增删改）只覆盖 2/3/4，审批提醒（1）由审批流产生、不由人工维护。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notice")
public class SysNoticeDO extends BaseDO {

    /** 通知ID（雪花ID） */
    @TableId
    private Long noticeId;

    /** 标题 */
    private String title;

    /** 内容（TEXT） */
    private String content;

    /** 通知类型：1 审批提醒 / 2 公海回收提醒 / 3 系统公告 / 4 任务提醒 */
    private Integer noticeType;

    /** 关联业务类型（如 customer / lead，纯展示/跳转用，可空） */
    private String bizType;

    /** 关联业务ID（可空） */
    private Long bizId;

    /** 接收人ID。⚠️ null = 全员可见（公告）；查询必须带接收人条件 */
    private Long receiverId;

    /** 是否已读：0 未读 / 1 已读 */
    private Integer isRead;

    /** 阅读时间（可空，未读为 null） */
    private LocalDateTime readTime;

    /** 是否已处理：0 未处理 / 1 已处理 */
    private Integer handleStatus;

    /** 处理时间（可空，未处理为 null） */
    private LocalDateTime handleTime;

    /** 发送时间（列表按它倒序） */
    private LocalDateTime sendTime;
}
