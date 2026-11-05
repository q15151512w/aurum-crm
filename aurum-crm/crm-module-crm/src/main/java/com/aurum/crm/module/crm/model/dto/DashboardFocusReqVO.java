package com.aurum.crm.module.crm.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 首页看板「我关注的」查询请求，对应 {@code GET /dashboard/focus}（《05-接口文档》9.3）。
 *
 * <p>按 {@code tab} 分发到对应模块的分页查询，所以**响应里的字段随 tab 变化**，
 * 与那个模块自己的列表接口完全一致（码值、{@code id} / {@code name} 别名、
 * {@code region} 派生字段都是现成的，不在这里重新拼）。
 *
 * <h3>⚠️ {@code scope} 的取值与各模块列表**必须一致**</h3>
 *
 * <p>这个参数曾经有过一次语义串台：《05-接口文档》9.3 与前端 {@code MyFocus.vue}
 * 都写成 {@code mine} = 我关注的 / {@code subordinate} = 我负责的，
 * 而**各模块列表页**（{@code lead/index.vue} 等的 FILTERS）里是
 * {@code mine} = 我负责的 / {@code subordinate} = 下属负责的 / {@code starred} = 我关注的。
 *
 * <p>同一个参数名在两个接口里表达不同含义，是必然会踩的坑 —— 已按**模块列表的口径**
 * 统一（见《开发注意事项》第 9 条：字段口径以模块列表为准）：
 *
 * <pre>
 *   starred  我关注的    ← 走 crm_star 关注表
 *   mine     我负责的    ← owner_user_id = 当前登录人
 *   all      全部（受数据权限约束）
 * </pre>
 *
 * <p>默认 {@code mine}（我负责的）—— 打开面板先看「自己的单子」比先看「关注的一小撮」有用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "首页看板-我关注的查询")
public class DashboardFocusReqVO extends PageParam {

    @Schema(description = "单据类型", example = "lead",
            allowableValues = {"lead", "customer", "opportunity", "quotation", "contract", "payment", "invoice"})
    private String tab = "lead";

    @Schema(description = "口径：starred 我关注的 / mine 我负责的 / all 全部", example = "mine",
            allowableValues = {"starred", "mine", "all"})
    private String scope = "mine";
}
