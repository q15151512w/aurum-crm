package com.aurum.crm.module.crm.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 商机列表分页查询请求。
 *
 * <p>前端 {@code views/crm/opportunity/index.vue} 固定只发四个参数：
 * {@code pageNo}、{@code pageSize}、{@code scope}（筛选胶囊）、{@code keyword}
 * —— 见 {@code components/ProList.vue} 与 {@code composables/useTable.js}。
 * 其余筛选项保留给 Knife4j / curl 调试。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "商机分页查询")
public class OpportunityPageReqVO extends PageParam {

    /**
     * 关键字，同时匹配**商机名称**或**负责人姓名**。
     *
     * <p>前端搜索框占位写的就是「商机名称/负责人」，所以两列都要匹配。
     * 负责人姓名在 {@code sys_user} 里，实现上先按姓名查出用户 ID 再 {@code IN}（见 Service）。
     */
    @Schema(description = "关键字：同时匹配商机名称或负责人姓名")
    private String keyword;

    /**
     * 筛选胶囊（前端 {@code FILTERS}）：{@code all} / {@code mine} / {@code subordinate} / {@code starred}。
     *
     * <p>取值语义（与角色数据权限是 <b>AND 交集</b> —— 只能继续收窄，不能靠它越权）。
     * 口径详见 {@code docs/09-商机模块字段契约.md} 5.2 与 {@code docs/10-商机模块接口文档.md} 4.1：
     * <ul>
     *     <li>{@code all}：不额外筛选</li>
     *     <li>{@code mine}：{@code owner_user_id = 登录人}</li>
     *     <li>{@code subordinate}：{@code owner_user_id IN (沿 sys_user.superior_id 递归的全部下属，不含自己)}；
     *       <b>没有下级 → 空分页</b>（不报错、也不退化成本部门查）</li>
     *     <li>{@code starred}：{@code opportunity_id IN (当前登录人在 crm_star 里关注过的商机)}；
     *       <b>一条都没关注过 → 空分页</b></li>
     * </ul>
     */
    @Schema(description = "筛选胶囊：all=全部 / mine=我负责的 / subordinate=下属负责的 / starred=我关注的",
            example = "all")
    private String scope;

    /** 销售阶段（**中文标签**，与前端下拉口径一致，如「商务谈判」） */
    @Schema(description = "销售阶段（中文标签）", example = "商务谈判")
    private String stage;

    @Schema(description = "关联客户ID")
    private Long customerId;

    @Schema(description = "归属人ID")
    private Long ownerUserId;

    @Schema(description = "创建时间-开始", example = "2026-01-01 00:00:00")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTimeStart;

    @Schema(description = "创建时间-结束", example = "2026-12-31 23:59:59")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTimeEnd;
}
