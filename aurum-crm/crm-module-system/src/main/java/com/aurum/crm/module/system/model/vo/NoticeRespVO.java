package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知列表 / 管理端返回。
 *
 * <p>{@code id} 是前端 {@code ProList} 拿的主键；{@code type} / {@code typeName} 与
 * 看板 {@code DashboardMessageRespVO} 共用一套前端标签语义（{@code notice} 通知 /
 * {@code announcement} 公告），便于前端直接复用配色。
 */
@Data
@Schema(description = "消息通知")
public class NoticeRespVO {

    @Schema(description = "通知ID（雪花ID）")
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "通知类型码值：1 审批提醒 / 2 公海回收提醒 / 3 系统公告 / 4 任务提醒")
    private Integer noticeType;

    @Schema(description = "机器可读的类型标识：notice 通知 / announcement 公告 / approval 审批")
    private String type;

    @Schema(description = "展示用的中文标签：通知 / 公告 / 审批")
    private String typeName;

    @Schema(description = "关联业务类型")
    private String bizType;

    @Schema(description = "关联业务ID")
    private Long bizId;

    @Schema(description = "接收人ID；null = 全员公告")
    private Long receiverId;

    @Schema(description = "接收人姓名（跨表补值；全员公告为 null）")
    private String receiverName;

    @Schema(description = "是否已读：0 未读 / 1 已读")
    private Integer isRead;

    @Schema(description = "是否已处理：0 未处理 / 1 已处理")
    private Integer handleStatus;

    @Schema(description = "发送时间")
    private LocalDateTime sendTime;
}
