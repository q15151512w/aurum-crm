package com.aurum.crm.module.crm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.DashboardFocusReqVO;
import com.aurum.crm.module.crm.model.dto.DashboardMessageReqVO;
import com.aurum.crm.module.crm.model.vo.DashboardMessageRespVO;
import com.aurum.crm.module.crm.model.vo.DashboardShortcutRespVO;
import com.aurum.crm.module.crm.model.vo.DashboardStatsRespVO;
import com.aurum.crm.module.crm.model.vo.DashboardTodoRespVO;
import com.aurum.crm.module.crm.model.vo.DashboardTrendPointRespVO;
import com.aurum.crm.module.crm.model.vo.OpportunityFunnelRespVO;
import com.aurum.crm.module.crm.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页看板（《05-接口文档》第九节）。
 *
 * <h3>几点约定</h3>
 *
 * <ul>
 *     <li><b>全部只读</b>：8 个端点都是 GET，没有任何副作用。</li>
 *     <li><b>数据权限自动生效</b>：涉及的业务表都在数据权限白名单里，
 *         {@code DataPermissionInnerInterceptor} 会自动追加行级条件，
 *         所以不同角色看到的数字不同（见 {@link DashboardService} 的注释）。</li>
 *     <li><b>权限点只有一个</b>：{@code dashboard:view}（种子里 4 个角色都有）。
 *         接口内部不再按模块细分权限 —— 首页是概览页，看不到某个模块的数据时，
 *         那个模块的计数自然就是 0，不需要额外报 403。</li>
 *     <li><b>金额字段有字段级权限</b>：{@code stats} / {@code trend} 里的金额对
 *         非财务角色返回 {@code null}（《05-接口文档》1.7），前端兜底成 {@code --}。</li>
 * </ul>
 */
@Tag(name = "首页看板")
@RestController
@RequestMapping("/dashboard")
@Validated
@SaCheckPermission("dashboard:view")
public class DashboardController {

    @Resource
    private DashboardService dashboardService;

    @Operation(summary = "9.1 待办事项：6 个待办指标计数")
    @GetMapping("/todo")
    public CommonResult<DashboardTodoRespVO> todo() {
        return CommonResult.success(dashboardService.todo());
    }

    @Operation(summary = "9.2 实时数据：7 个经营指标")
    @GetMapping("/stats")
    public CommonResult<DashboardStatsRespVO> stats(
            @Parameter(description = "统计维度：month 本月 / week 本周 / day 今日")
            @RequestParam(value = "range", required = false, defaultValue = "month") String range) {
        return CommonResult.success(dashboardService.stats(range));
    }

    @Operation(summary = "9.3 我关注的：按单据类型分页",
            description = "响应里的列表元素随 tab 变化，字段与该模块的列表接口完全一致")
    @GetMapping("/focus")
    public CommonResult<PageResult<Object>> focus(DashboardFocusReqVO reqVO) {
        return CommonResult.success(dashboardService.focus(reqVO));
    }

    @Operation(summary = "9.4 消息：审批 + 通知 + 公告")
    @GetMapping("/messages")
    public CommonResult<PageResult<DashboardMessageRespVO>> messages(DashboardMessageReqVO reqVO) {
        return CommonResult.success(dashboardService.messages(reqVO));
    }

    @Operation(summary = "9.5 公告 | 通知：数据源限定为通知与公告")
    @GetMapping("/notices")
    public CommonResult<PageResult<DashboardMessageRespVO>> notices(DashboardMessageReqVO reqVO) {
        return CommonResult.success(dashboardService.notices(reqVO));
    }

    @Operation(summary = "9.6 快捷入口：按当前登录人的菜单权限过滤")
    @GetMapping("/shortcuts")
    public CommonResult<List<DashboardShortcutRespVO>> shortcuts() {
        return CommonResult.success(dashboardService.shortcuts());
    }

    /**
     * 销售漏斗。
     *
     * <p>返回结构与 {@code GET /crm/opportunity/funnel} 完全一致（同一个 Service 方法），
     * 前端 {@code FunnelChart} 两个入口共用一个组件。
     */
    @Operation(summary = "9.7 销售漏斗（按商机阶段）")
    @GetMapping("/funnel")
    public CommonResult<List<OpportunityFunnelRespVO>> funnel() {
        return CommonResult.success(dashboardService.funnel());
    }

    @Operation(summary = "9.8 业绩趋势（按天/小时）")
    @GetMapping("/trend")
    public CommonResult<List<DashboardTrendPointRespVO>> trend(
            @Parameter(description = "统计维度：month 本月 / week 本周 / day 今日")
            @RequestParam(value = "range", required = false, defaultValue = "month") String range) {
        return CommonResult.success(dashboardService.trend(range));
    }
}
