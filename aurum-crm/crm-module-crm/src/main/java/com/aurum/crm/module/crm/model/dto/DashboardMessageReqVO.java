package com.aurum.crm.module.crm.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 首页看板「消息」/「公告 | 通知」查询请求
 * （{@code GET /dashboard/messages}、{@code GET /dashboard/notices}，文档 9.4 / 9.5）。
 *
 * <p>两个接口共用本 DTO —— 文档 9.5 明确写了「结构与 messages 完全一致，
 * 只是数据源限定为公告与通知」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "首页看板-消息/公告查询")
public class DashboardMessageReqVO extends PageParam {

    @Schema(description = "按类型过滤；不传表示全部", example = "approval",
            allowableValues = {"approval", "notice", "announcement"})
    private String type;
}
