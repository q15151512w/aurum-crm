package com.aurum.crm.module.system.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息通知分页查询，两个入口共用：
 * <ul>
 *     <li>「我的消息」：{@code GET /system/notice} —— 由服务端注入 {@code receiver_id = 我 OR NULL}；
 *         前端可传 {@code noticeType} / {@code isRead} 过滤。</li>
 *     <li>「公告/通知管理」：{@code GET /system/notice/manage} —— 服务端限定
 *         {@code notice_type IN (2,3,4)}，不受接收人限制。</li>
 * </ul>
 *
 * <p>{@code keyword} 匹配标题；{@code noticeType} / {@code isRead} 缺省表示不筛选。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "消息通知分页查询")
public class NoticePageReqVO extends PageParam {

    @Schema(description = "关键字，匹配标题（空串等于不筛）", example = "公海")
    private String keyword;

    @Schema(description = "通知类型：1 审批提醒 / 2 公海回收提醒 / 3 系统公告 / 4 任务提醒；不传 = 全部", example = "3")
    private Integer noticeType;

    @Schema(description = "是否已读：0 未读 / 1 已读；不传 = 全部", example = "0")
    private Integer isRead;
}
