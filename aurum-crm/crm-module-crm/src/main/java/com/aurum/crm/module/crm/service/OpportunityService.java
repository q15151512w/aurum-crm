package com.aurum.crm.module.crm.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.DocumentItemSaveReqVO;
import com.aurum.crm.module.crm.model.dto.OpportunityPageReqVO;
import com.aurum.crm.module.crm.model.dto.OpportunitySaveReqVO;
import com.aurum.crm.module.crm.model.dto.OpportunitySimplePageReqVO;
import com.aurum.crm.module.crm.model.dto.OpportunityStageChangeReqVO;
import com.aurum.crm.module.crm.model.vo.OpportunityFunnelRespVO;
import com.aurum.crm.module.crm.model.vo.OpportunityRespVO;
import com.aurum.crm.module.crm.model.vo.OpportunitySimpleRespVO;
import com.aurum.crm.module.crm.model.vo.OpportunityStageLogRespVO;

import java.util.List;

/**
 * 商机 Service：列表 / 详情 / 新增 / 修改 / 删除 / 变更阶段 / 阶段历史 / 选择器 / 漏斗。
 *
 * <p>路由见《05-接口文档》5.1 商机。字段名与错误口径见
 * {@code docs/09-商机模块字段契约.md}。
 *
 * <p><b>本轮不做（原因见字段契约文档「不在本次范围」）</b>：
 * <ul>
 *     <li>{@code GET /crm/opportunity/export} —— 项目没有 Excel 依赖，
 *       与产品模块的导出同样留作待办</li>
 * </ul>
 *
 * <p>实现类：{@link com.aurum.crm.module.crm.service.impl.OpportunityServiceImpl}。
 */
public interface OpportunityService {

    /**
     * 商机分页列表。
     *
     * <p>自动注入数据权限（见《04-权限设计文档》3.4：{@code crm_opportunity} **没有公海概念**，
     * 条件是纯 {@code <scope>}），并额外响应前端的筛选胶囊
     * {@code mine} / {@code subordinate}。
     */
    PageResult<OpportunityRespVO> pageQuery(OpportunityPageReqVO reqVO);

    /**
     * 商机详情。
     *
     * <p><b>含产品明细</b>（{@code products}）：前端详情页的「添加产品」弹窗靠它回填编辑器，
     * 保存时走 {@link #replaceProducts} 整份覆盖。
     */
    OpportunityRespVO get(Long id);

    /**
     * 新增商机。
     *
     * <p>商机编号由服务端生成；{@code owner_user_id} 缺省为当前登录人；
     * {@code dept_id} 跟着**负责人**所在部门走（不是创建人）。
     *
     * @return 新增商机的 ID
     */
    Long create(OpportunitySaveReqVO reqVO);

    /**
     * 修改商机。
     *
     * <p>两条容易被忽略的规则：
     * <ol>
     *     <li>从编辑表单里改阶段也会写一条阶段变更日志 ——
     *       否则用户从「编辑」弹窗改的阶段在「阶段变更历史」里查不到，历史就断层了</li>
     *     <li>{@code expectDealTime} 传**空串**表示清空该字段。全局
     *       {@code update-strategy: not_null} 会把 {@code null} 当成「不改这一列」，
     *       所以清空走的是显式 {@code SET}，见实现类的注释</li>
     * </ol>
     */
    void update(Long id, OpportunitySaveReqVO reqVO);

    /**
     * 删除商机（逻辑删除）。
     *
     * <p>阶段变更历史**不删**：它是审计流水，商机被删后仍应可追溯。
     */
    void delete(Long id);

    /**
     * 变更阶段，并写一条阶段变更日志。
     *
     * <p><b>幂等</b>：目标阶段与当前阶段相同时直接返回，不写历史
     * （否则「变更成功」却只多了一条「A → A」的噪声记录）。
     */
    void changeStage(Long id, OpportunityStageChangeReqVO reqVO);

    /**
     * 阶段变更历史（不分页，最近一次变更排在最前）。
     *
     * <p>商机不存在时抛 {@code 2201} 而不是返回空数组 ——
     * 否则前端分不清「确实没有变更记录」和「id 传错了」。
     */
    List<OpportunityStageLogRespVO> listStageLogs(Long id);

    /**
     * 商机选择器（通用选择器接口约定，见《05-接口文档》第十节）。
     *
     * <p>同样受数据权限约束：不能被本用户看到的商机也不该出现在选择器里。
     */
    PageResult<OpportunitySimpleRespVO> simpleList(OpportunitySimplePageReqVO reqVO);

    /**
     * <b>追加</b>产品明细（{@code POST /crm/opportunity/{id}/products}）。
     *
     * <p>新行接在已有明细之后：{@code sort} 从「现有最大 sort + 1」开始递增，**不动已有行**。
     * 因此校验的是「已有 + 新增」的总行数不超过 {@code ItemSupport.MAX_ITEMS}（200）。
     *
     * @param reqVO 明细行数组；空数组 = 什么都不做（幂等成功）
     */
    void addProducts(Long id, List<DocumentItemSaveReqVO> reqVO);

    /**
     * <b>整份覆盖</b>产品明细（{@code PUT /crm/opportunity/{id}/products}）。
     *
     * <p>先逻辑删除旧行（留痕）再插入新行，{@code sort} 按提交顺序写 {@code 1..n} ——
     * 前端详情页的弹窗既能加、也能改数量和删除，保存时走的就是这个接口。
     *
     * @param reqVO 明细行数组；空数组 = 清空全部明细
     */
    void replaceProducts(Long id, List<DocumentItemSaveReqVO> reqVO);

    /**
     * 关注商机（{@code POST /crm/opportunity/{id}/star}）。
     *
     * <p><b>幂等</b>：重复关注不报错、不会多写一行（通用关注表 {@code crm_star} 的
     * {@code uk_biz_user} 唯一键兜底，见 {@code StarServiceImpl}）。
     *
     * <p>刻意做成 {@code star} / {@link #unstar} 一对而不是单个 toggle 接口：toggle 不幂等，
     * 双击或请求重试会把状态翻回去，让图标和库里相反 —— 星标是个裸图标，恰恰是最容易双击的控件。
     *
     * <p>商机不存在（或不在当前用户数据范围内）→ {@code 2201}，与删除同一口径。
     */
    void star(Long id);

    /**
     * 取消关注商机（{@code DELETE /crm/opportunity/{id}/star}），**幂等**：未关注过也不报错。
     */
    void unstar(Long id);

    /**
     * 销售漏斗统计（首页看板「销售漏斗」区块）。
     *
     * <p>固定返回 初步接洽 / 需求确认 / 方案报价 / 商务谈判 / 赢单 五行
     * （输单是终态、不属于推进过程），没有数据的阶段也会返回，避免前端图表错位。
     */
    List<OpportunityFunnelRespVO> funnel();

    /**
     * 导出商机（Excel → OSS → 返回下载 URL），含当前过滤条件。
     */
    String export(OpportunityPageReqVO reqVO);

    /**
     * 导出商机（XML/SpreadsheetML → OSS → 返回下载 URL），含当前过滤条件。
     */
    String exportXml(OpportunityPageReqVO reqVO);
}
