package com.aurum.crm.module.crm.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.LeadPageReqDTO;
import com.aurum.crm.module.crm.model.dto.LeadSaveReqDTO;
import com.aurum.crm.module.crm.model.dto.ToLeadPoolReqDTO;
import com.aurum.crm.module.crm.model.dto.LeadSimplePageReqDTO;
import com.aurum.crm.module.crm.model.vo.LeadRespVO;
import com.aurum.crm.module.crm.model.vo.LeadSimpleRespVO;

import java.util.List;

/**
 * 线索 Service 接口：列表 / 详情 / 新增 / 修改 / 删除 / 放入线索池 / 关注。
 *
 * <p>列表查询自动注入数据权限（本人+线索池 / 本部门及以下+线索池 / 全部），
 * 与《04-权限设计文档》第三节一致；线索池列表单独走 {@link LeadPoolService}。
 *
 * <p>列表还要叠加请求参数 {@code scope} 的取数口径（全部 / 我负责的 / 下属负责的 /
 * 我关注的），它与数据权限是<b>交集</b>关系 —— 只能在权限范围内继续收窄。
 *
 * <p>实现类：{@link com.aurum.crm.module.crm.service.impl.LeadServiceImpl}。
 */
public interface LeadService {

    /**
     * 线索分页列表（含数据权限过滤 + {@code scope} 取数口径）。
     */
    PageResult<LeadRespVO> pageQuery(LeadPageReqDTO reqDTO);

    /**
     * 线索详情。
     */
    LeadRespVO get(Long id);

    /**
     * 线索选择器（{@code GET /crm/lead/simple-list}）分页列表，只取选择器要读的几列。
     */
    PageResult<LeadSimpleRespVO> simpleList(LeadSimplePageReqDTO reqDTO);

    /**
     * 新增线索。
     *
     * @return 新增线索的 ID
     */
    Long create(LeadSaveReqDTO reqDTO);

    /**
     * 修改线索。
     */
    void update(Long id, LeadSaveReqDTO reqDTO);

    /**
     * 删除线索（逻辑删除，走 delFlag）。
     */
    void delete(Long id);

    /**
     * 批量删除线索（逻辑删除）。
     *
     * <p>与 {@link #delete(Long)} 不同，本方法<b>容忍不存在的 id</b>：
     * 批量操作里某条已被别人删掉是常态，为它整批失败会让用户反复重试。
     *
     * @param ids 线索ID集合；为空则什么都不做
     */
    void deleteBatch(List<Long> ids);

    /**
     * 放入线索池（负责人置空 + 记录进入线索池时间/原因）。
     */
    void putToPool(Long id, ToLeadPoolReqDTO reqDTO);

    /**
     * 关注线索（幂等）。
     *
     * @throws com.aurum.crm.common.exception.ServiceException 线索不存在时抛 2001
     */
    void star(Long id);

    /**
     * 取消关注线索（幂等：没关注过也不报错）。
     */
    void unstar(Long id);

    /**
     * 线索转客户：
     * <ol>
     *     <li>按线索字段生成一条客户记录（{@code leadId} 回填进客户，建立溯源）；</li>
     *     <li>在线索上记下 {@code convertedCustomerId} 与 {@code convertTime}；</li>
     *     <li>写一条线索归属流转记录（{@code action = 6 线索转化}）。</li>
     * </ol>
     *
     * @return 新生成客户的 ID
     * @throws com.aurum.crm.common.exception.ServiceException 线索不存在抛 2001、
     *         已转化抛 2002
     */
    Long convert(Long id);

    /**
     * 导出线索（Excel → OSS → 返回下载 URL），含当前过滤条件。
     */
    String export(LeadPageReqDTO reqDTO);

    /**
     * 导出线索（XML/SpreadsheetML → OSS → 返回下载 URL），含当前过滤条件。
     */
    String exportXml(LeadPageReqDTO reqDTO);
}
