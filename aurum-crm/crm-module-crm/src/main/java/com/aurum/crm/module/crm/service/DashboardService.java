package com.aurum.crm.module.crm.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.DashboardFocusReqVO;
import com.aurum.crm.module.crm.model.dto.DashboardMessageReqVO;
import com.aurum.crm.module.crm.model.vo.DashboardMessageRespVO;
import com.aurum.crm.module.crm.model.vo.DashboardShortcutRespVO;
import com.aurum.crm.module.crm.model.vo.DashboardStatsRespVO;
import com.aurum.crm.module.crm.model.vo.DashboardTodoRespVO;
import com.aurum.crm.module.crm.model.vo.DashboardTrendPointRespVO;
import com.aurum.crm.module.crm.model.vo.OpportunityFunnelRespVO;

import java.util.List;

/**
 * 首页看板（《05-接口文档》第九节，8 个接口）。
 *
 * <p>全部**只读**、无副作用。数据权限对白名单内的表自动生效
 * （见 {@code DataPermissionInnerInterceptor}），本接口不需要额外写过滤。
 *
 * <p>放在 crm 模块的原因见 {@code FinPaymentMiniDO} 的类注释。
 */
public interface DashboardService {

    /** 9.1 待办事项：6 个计数 */
    DashboardTodoRespVO todo();

    /**
     * 9.2 实时数据：7 个经营指标。
     *
     * @param range 统计维度 {@code month} 本月 / {@code week} 本周 / {@code day} 今日，缺省 {@code month}
     */
    DashboardStatsRespVO stats(String range);

    /**
     * 9.3 我关注的：按单据类型分页。
     *
     * <p>返回的列表元素是**对应模块自己的 RespVO**，字段口径与该模块的列表接口完全一致。
     * 因为要兼容 7 种单据类型，返回类型只能是「列表」这一种宽类型。
     */
    PageResult<Object> focus(DashboardFocusReqVO reqVO);

    /** 9.4 消息：审批 + 通知 + 公告（按接收人/审批人过滤） */
    PageResult<DashboardMessageRespVO> messages(DashboardMessageReqVO reqVO);

    /** 9.5 公告 | 通知：数据源限定为通知与公告 */
    PageResult<DashboardMessageRespVO> notices(DashboardMessageReqVO reqVO);

    /** 9.6 快捷入口：按当前登录人的菜单权限过滤 */
    List<DashboardShortcutRespVO> shortcuts();

    /**
     * 9.7 销售漏斗。
     *
     * <p>直接**委托** {@code OpportunityService#funnel()} —— 两者返回的结构完全一致
     * （{@code [{stage, count, amount}]}，固定 5 行、不含输单），
     * 各自维护一份只会让两处慢慢漂移。
     */
    List<OpportunityFunnelRespVO> funnel();

    /**
     * 9.8 业绩趋势。
     *
     * @param range 与 {@link #stats(String)} 共用取值
     */
    List<DashboardTrendPointRespVO> trend(String range);
}
