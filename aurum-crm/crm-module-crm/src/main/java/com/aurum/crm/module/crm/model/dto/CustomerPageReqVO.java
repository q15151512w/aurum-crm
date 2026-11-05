package com.aurum.crm.module.crm.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 客户列表 / 公海列表 分页查询请求，与原型搜索区字段对齐：
 * 客户名称、负责人、联系电话 + 查询/重置。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "客户分页查询")
public class CustomerPageReqVO extends PageParam {

    @Schema(description = "客户名称（模糊）")
    private String customerName;

    @Schema(description = "负责人ID")
    private Long ownerUserId;

    @Schema(description = "所属行业（字典 crm_industry）")
    private Integer industry;

    @Schema(description = "客户来源（字典 crm_source）")
    private Integer source;

    @Schema(description = "客户级别（1重要 2普通 3非优先）")
    private Integer level;

    @Schema(description = "关键字（客户名称/联系电话）")
    private String keyword;

    @Schema(description = "成交状态（1未成交 2已成交）")
    private Integer dealStatus;

    @Schema(description = "列表口径：all 全部 / mine 我负责的 / subordinate 下属负责的 / starred 我关注的 / dealt 已成交 / undealt 未成交",
            example = "all", allowableValues = {"all", "mine", "subordinate", "starred", "dealt", "undealt"})
    private String scope;

    @Schema(description = "创建时间-开始", example = "2026-01-01 00:00:00")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTimeStart;

    @Schema(description = "创建时间-结束", example = "2026-12-31 23:59:59")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTimeEnd;
}
