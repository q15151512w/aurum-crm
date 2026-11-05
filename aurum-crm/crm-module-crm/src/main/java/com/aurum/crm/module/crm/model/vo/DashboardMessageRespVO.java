package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 首页看板的「消息」与「公告 | 通知」两个区块共用同一个响应结构
 * （《05-接口文档》9.4 / 9.5 —— 文档明确写了「结构与 messages 完全一致」）。
 *
 * <p>字段名与前端 {@code components/MessagePanel.vue} 的 props 对齐。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "首页看板-消息/公告行")
public class DashboardMessageRespVO {

    /**
     * 行标识。
     *
     * <p>⚠️ 审批与通知是**两张不同的表**，主键各自从 1 开始 —— 直接返回会撞车。
     * 所以审批行的 id 用 {@code 业务ID}，通知行用 {@code 通知ID}，
     * 并且两个列表**不会混在同一个响应里**（`/messages` 只返回审批，
     * `/notices` 只返回通知公告），不会真的冲突。
     */
    @Schema(description = "行标识")
    private Long id;

    /**
     * 机器可读的类型标识，前端据此决定标签配色。
     *
     * <p>取值：{@code approval} 审批 / {@code notice} 通知 / {@code announcement} 公告。
     * 前端刻意**按 type 配色而不按 typeName** —— 中文标签将来可能改，type 是契约。
     */
    @Schema(description = "类型标识：approval / notice / announcement", example = "approval")
    private String type;

    @Schema(description = "类型的中文标签", example = "审批")
    private String typeName;

    @Schema(description = "标题", example = "张三的回款审批，申请单号：42506380001")
    private String title;

    /**
     * 点击后的跳转路径，无跳转时为 {@code null}。
     *
     * <p>前端 {@code MessagePanel} 对 null 做了判断，不会渲染成可点链接。
     */
    @Schema(description = "跳转路径，无跳转时为 null", example = "/finance/payment-audit")
    private String link;

    /**
     * 展示用的时间字符串。
     *
     * <p>刻意用 {@code String} 而不是 {@code LocalDateTime}：这个字段是**给人看的**，
     * 前端直接渲染，不做任何格式化。传时间对象会让前端拿到 ISO 串再自己转，
     * 反而多一层。格式固定 {@code yyyy-MM-dd HH:mm}。
     */
    @Schema(description = "展示用时间，格式 yyyy-MM-dd HH:mm", example = "2025-06-26 14:38")
    private String time;
}
