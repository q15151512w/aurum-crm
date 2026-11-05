package com.aurum.crm.module.crm.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 报价单列表分页查询请求。
 *
 * <p>前端 {@code views/crm/quotation/index.vue} 通过 {@code ProList} 固定发四个参数：
 * {@code pageNo}、{@code pageSize}、{@code scope}（筛选胶囊）、{@code keyword}。
 * 其余筛选项保留给 Knife4j / curl 调试。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "报价单分页查询")
public class QuotationPageReqVO extends PageParam {

    /**
     * 关键字，同时匹配**报价单名称**或**商机名称**。
     *
     * <p>前端搜索框占位写的就是「报价单名称/商机名称」，所以两列都要匹配。
     * 商机名称在 {@code crm_opportunity} 里，实现上先按名称查出商机 ID 再 {@code IN}。
     */
    @Schema(description = "关键字：同时匹配报价单名称或商机名称")
    private String keyword;

    /**
     * 筛选胶囊（前端 {@code FILTERS} 共 7 个取值）。
     *
     * <p>两类语义：
     * <ul>
     *     <li>数据范围：{@code all} / {@code mine} / {@code subordinate} / {@code starred}</li>
     *     <li>状态：{@code quoting}（报价中，含库里的 0 草稿与 1 已发出）、
     *       {@code contracted}（已生成合同）、{@code expired}（已失效，含 3 已拒绝与 4 已作废）</li>
     * </ul>
     */
    @Schema(description = "筛选胶囊：all / mine / subordinate / starred / quoting / contracted / expired",
            example = "all")
    private String scope;

    /** 状态筛选（**中文标签**，与前端下拉口径一致） */
    @Schema(description = "状态（中文标签）", example = "报价中")
    private String status;

    @Schema(description = "关联客户ID")
    private Long customerId;

    @Schema(description = "关联商机ID")
    private Long opportunityId;

    @Schema(description = "归属人ID")
    private Long ownerUserId;

    @Schema(description = "创建时间-开始", example = "2026-01-01 00:00:00")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTimeStart;

    @Schema(description = "创建时间-结束", example = "2026-12-31 23:59:59")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTimeEnd;
}
