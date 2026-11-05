package com.aurum.crm.module.crm.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.QuotationPageReqVO;
import com.aurum.crm.module.crm.model.dto.QuotationSaveReqVO;
import com.aurum.crm.module.crm.model.dto.QuotationSimplePageReqVO;
import com.aurum.crm.module.crm.model.vo.QuotationRespVO;
import com.aurum.crm.module.crm.model.vo.QuotationSimpleRespVO;

/**
 * 报价单 Service：列表 / 详情 / 新增 / 修改 / 删除 / 选择器（明细随主单一起读写）。
 *
 * <p>路由见《05-接口文档》5.2 报价单。字段名、金额口径与有意偏差见
 * {@code docs/11-报价单模块字段契约.md}。
 *
 * <p><b>本轮不做</b>：
 * <ul>
 *     <li>{@code GET /crm/quotation/export} —— 项目没有 Excel 依赖，且 {@code sys_menu} 里
 *       没有 {@code crm:quotation:export} 权限点（与产品 / 商机模块同一待办）</li>
 *     <li>「我关注的」星标落库 —— 关注表尚未建表</li>
 *     <li>独立的 {@code /{id}/products} 端点 —— 《05-接口文档》5.2 没有这个端点，
 *       前端是把 {@code products} 内嵌在主单 payload 里提交的</li>
 * </ul>
 *
 * <p>实现类：{@link com.aurum.crm.module.crm.service.impl.QuotationServiceImpl}。
 */
public interface QuotationService {

    /**
     * 报价单分页列表。
     *
     * <p>自动注入数据权限（《04-权限设计文档》3.4：{@code crm_quotation} **没有公海概念**，
     * 条件是纯 {@code <scope>}），并额外响应前端 7 个筛选胶囊
     * （4 个数据范围 + 3 个状态）。列表会一并算出「商品总数」与「整单利润」。
     */
    PageResult<QuotationRespVO> pageQuery(QuotationPageReqVO reqVO);

    /**
     * 报价单详情，**含产品明细**。
     *
     * <p>明细必须返回：前端编辑流程是「详情 → 回填明细编辑器 → PUT 整份覆盖」，
     * 少了明细一次编辑就会把明细洗掉。
     */
    QuotationRespVO get(Long id);

    /**
     * 新增报价单。
     *
     * <p>编号与版本号由服务端生成；{@code ownerUserId} 缺省为当前登录人；
     * {@code deptId} 跟着**负责人**所在部门走；三个金额与每行 {@code amount}
     * 一律按《02-建表文档》3.6 从明细重算，不采信客户端提交的金额。
     *
     * @return 新增报价单的 ID
     */
    Long create(QuotationSaveReqVO reqVO);

    /**
     * 修改报价单。
     *
     * <p><b>明细的两种语义</b>：{@code products == null} → 不动明细、也不重算金额；
     * {@code products} 为数组（含空数组）→ 整份覆盖（先逻辑删除旧行再插入新行）并按新明细重算金额。
     *
     * <p>状态列还有一层「防漂移」：请求标签若与**当前库值的归并标签**相同，就不动 {@code status}，
     * 避免一条 {@code 0 草稿} / {@code 3 已拒绝} 的单子被编辑一次就静默变成 {@code 1} / {@code 4}。
     */
    void update(Long id, QuotationSaveReqVO reqVO);

    /**
     * 删除报价单（逻辑删除）。
     *
     * <p>明细不跟着删：主单查不到了，明细自然不会被读到，保留以便追溯。
     */
    void delete(Long id);

    /**
     * 报价单选择器（通用选择器接口约定，见《05-接口文档》第十节）。
     *
     * <p>合同表单在用它选报价单，所以同样受数据权限约束 ——
     * 看不到的报价单不该出现在选择器里。
     *
     * <p><b>每行带上 {@code products}（产品明细）</b>：合同表单的「报价单编号」选择器
     * 选中一张报价单后，要把这些行直接填进合同的产品明细，所以明细必须随选择器一起下发 ——
     * 只给 {@code id / name / quotationNo} 的话，选完报价单产品的位置还是空的。
     * 明细结构与详情接口的 {@code products} 相同，且已跨表补好 {@code unitName}
     * （前端明细的「单位」是下拉，缺名字那一格就是空的）。
     * 没有明细的报价单给**空数组**，不是 {@code null}。
     *
     * <p>另外带上 {@code opportunityId / opportunityName / customerId}：合同表单选中报价单后
     * 要把**商机与客户一起对齐**成这张报价单上的值（口径见
     * {@code com.aurum.crm.module.crm.support.ContractRelationSupport}），
     * 只给 id 会让表单上的名称停在旧值 —— 那是「界面显示 A、落库 B」这类最难查的问题。
     *
     * <p>{@code reqVO} 的 {@code opportunityId / customerId} 是**可选的收窄条件**
     * （合同表单按所选商机 / 客户传）：不传即不过滤，行为与改动前一致。
     */
    PageResult<QuotationSimpleRespVO> simpleList(QuotationSimplePageReqVO reqVO);

    /**
     * 关注报价单（{@code POST /crm/quotation/{id}/star}）。
     *
     * <p><b>幂等</b>：重复关注不报错、不会多写一行（通用关注表 {@code crm_star} 的
     * {@code uk_biz_user} 唯一键兜底，见 {@code StarServiceImpl}）。
     *
     * <p>与商机 / 线索 / 客户同一形态，刻意做成 {@code star} / {@link #unstar} 一对而不是
     * 单个 toggle：toggle 不幂等，双击会把状态翻回去。
     * 报价单不存在（或不在数据范围内）→ {@code 2251}（{@code QUOTATION_NOT_EXISTS}）。
     */
    void star(Long id);

    /**
     * 取消关注报价单（{@code DELETE /crm/quotation/{id}/star}），**幂等**：未关注过也不报错。
     */
    void unstar(Long id);

    /**
     * 导出报价单（Excel → OSS → 返回下载 URL），含当前过滤条件。
     */
    String export(QuotationPageReqVO reqVO);

    /**
     * 导出报价单（XML/SpreadsheetML → OSS → 返回下载 URL），含当前过滤条件。
     */
    String exportXml(QuotationPageReqVO reqVO);

    // ==================== 状态流转 ====================

    /**
     * 报价单转合同（{@code POST /crm/quotation/{id}/convert-contract}）。
     *
     * <p>按报价明细生成一份**合同草稿**，并把报价单状态置为「已生成合同」
     * （库值 {@code 2}，见 {@link com.aurum.crm.module.crm.model.QuotationStatus#CONTRACTED}）。
     *
     * <p><b>合同那一步复用同事已交付的 {@code ContractService#create}</b>：
     * 编号（{@code CONTRACT} 规则）、明细行金额按《02-建表文档》3.6 重算、
     * {@code amount = Σ 明细}、落到草稿待提审态，全部由它负责 —— 报价单侧不重复实现一遍，
     * 也就不存在两套金额口径。报价单侧只负责「凑出请求体」与「推进自己的状态」。
     *
     * <p><b>允许重复转换</b>：一个报价单可以出多份合同（1:N），所以「已生成合同」还能再转，
     * 每次生成一份新的合同草稿。判断口径见
     * {@link com.aurum.crm.module.crm.model.QuotationStatus#canConvert(Integer)}。
     *
     * <p><b>失败语义</b>：
     * <ul>
     *     <li>报价单不存在、或不在当前人的数据范围内 → {@code 2251}（{@code QUOTATION_NOT_EXISTS}）</li>
     *     <li>已失效（库值 3/4）→ {@code 2252}（{@code QUOTATION_STATUS_ERROR}）</li>
     *     <li>没有任何产品明细 → {@code 2252}（合同明细不允许为空，不能造一张空合同）</li>
     *     <li>并发下报价单状态已被他人改动 → {@code 2252}，且整笔回滚（不会留下孤儿合同）</li>
     * </ul>
     *
     * @return 新生成的合同 ID
     */
    Long convertToContract(Long id);

    /**
     * 作废报价单（{@code POST /crm/quotation/{id}/void}）。
     *
     * <p>状态落到库值 {@code 4}（已作废），对外归并显示为「已失效」
     * （见 {@link com.aurum.crm.module.crm.model.QuotationStatus#EXPIRED}）。写 4 而不是 3：
     * 3 是「已拒绝」（客户拒绝），语义不同但对外都显示「已失效」，写错在界面上看不出来。
     *
     * <p><b>只有「报价中」（库值 0/1）可以作废</b>：
     * 已生成合同的单子已经有下游合同（1:N），作废它会出现「报价单已失效、合同仍有效」的矛盾，
     * 应当先去处理合同；已失效是终态，重复作废没有意义。两者都抛 {@code 2252}。
     *
     * <p>报价单不存在或不在数据范围内 → {@code 2251}。
     */
    void voidQuotation(Long id);
}
