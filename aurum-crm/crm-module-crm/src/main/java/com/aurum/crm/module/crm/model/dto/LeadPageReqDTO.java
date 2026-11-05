package com.aurum.crm.module.crm.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 线索列表 / 线索池列表 分页查询请求，与《接口文档》4.1 查询参数对齐：
 * leadName、ownerUserId、source、industry、createTimeStart/End、keyword、scope。
 *
 * <p>{@code scope} 是列表页顶部筛选胶囊的取数口径，与角色数据权限是两个正交维度 ——
 * 它只能在数据权限允许的范围内继续收窄（见 {@code LeadServiceImpl#applyScope}）。
 * 本 DTO 也被 {@code LeadPoolController} 共用，但线索池页忽略 {@code scope}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "线索分页查询")
public class LeadPageReqDTO extends PageParam {

    @Schema(description = "线索名称（模糊）")
    private String leadName;

    @Schema(description = "负责人ID")
    private Long ownerUserId;

    @Schema(description = "线索来源（字典 crm_source）")
    private Integer source;

    @Schema(description = "所属行业（字典 crm_industry）")
    private Integer industry;

    @Schema(description = "关键字（线索名称/联系人姓名/联系电话）")
    private String keyword;

    @Schema(description = "列表口径：all 全部 / mine 我负责的 / subordinate 下属负责的 / starred 我关注的",
            example = "all", allowableValues = {"all", "mine", "subordinate", "starred"})
    private String scope;

    @Schema(description = "创建时间-开始", example = "2026-01-01 00:00:00")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTimeStart;

    @Schema(description = "创建时间-结束", example = "2026-12-31 23:59:59")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTimeEnd;
}
