package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 公告/通知新增 / 修改请求（管理端）。
 *
 * <p>{@code noticeType} 限定 2/3/4 —— 审批提醒（1）由审批流产生，不开放人工创建。
 * <ul>
 *     <li>{@code receiverId} 为 {@code null} → <b>全员公告</b>（{@code receiver_id IS NULL}），
 *         每个登录用户都能在「我的消息」里看到</li>
 *     <li>{@code receiverId} 为具体用户 → 定向通知，只有该用户可见</li>
 * </ul>
 */
@Data
@Schema(description = "公告/通知新增/修改")
public class NoticeSaveReqVO {

    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "系统维护公告")
    @NotBlank(message = "请输入标题")
    @Size(max = 255, message = "标题长度不能超过 255")
    private String title;

    @Schema(description = "内容（选填）", example = "本周日 02:00-04:00 维护")
    private String content;

    @Schema(description = "通知类型：2 公海回收提醒 / 3 系统公告 / 4 任务提醒", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    @Min(value = 2, message = "通知类型只能是 2 公海回收 / 3 系统公告 / 4 任务提醒")
    @Max(value = 4, message = "通知类型只能是 2 公海回收 / 3 系统公告 / 4 任务提醒")
    private Integer noticeType;

    @Schema(description = "接收人ID；不传 = 全员公告（receiver_id 置空）", example = "5")
    private Long receiverId;

    @Schema(description = "关联业务类型（选填，纯展示/跳转用）", example = "customer")
    @Size(max = 32, message = "关联业务类型长度不能超过 32")
    private String bizType;

    @Schema(description = "关联业务ID（选填）")
    private Long bizId;
}
