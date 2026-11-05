package com.aurum.crm.module.crm.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.CustomerPageReqVO;
import com.aurum.crm.module.crm.model.dto.CustomerSaveReqVO;
import com.aurum.crm.module.crm.model.dto.CustomerSimplePageReqVO;
import com.aurum.crm.module.crm.model.dto.ToPoolReqVO;
import com.aurum.crm.module.crm.model.vo.CustomerRespVO;
import com.aurum.crm.module.crm.model.vo.CustomerSimpleRespVO;
import com.aurum.crm.module.crm.model.vo.SalesOwnerRespVO;

import java.util.List;

/**
 * 客户 Service 接口：列表 / 详情 / 新增 / 修改 / 删除 / 放入公海。
 *
 * <p>列表查询自动注入数据权限（本人+公海 / 本部门及以下+公海 / 全部），
 * 与《04-权限设计文档》第三节一致；公海列表单独走 {@link CustomerPoolService}。
 *
 * <p>实现类：{@link com.aurum.crm.module.crm.service.impl.CustomerServiceImpl}。
 */
public interface CustomerService {

    /**
     * 客户分页列表（含数据权限过滤）。
     */
    PageResult<CustomerRespVO> pageQuery(CustomerPageReqVO reqVO);

    /**
     * 客户详情。
     */
    CustomerRespVO get(Long id);

    /**
     * 客户选择器（{@code GET /crm/customer/simple-list}）分页列表，只取选择器要读的几列。
     */
    PageResult<CustomerSimpleRespVO> simpleList(CustomerSimplePageReqVO reqVO);

    /**
     * 新增客户。
     *
     * @return 新增客户的 ID
     */
    Long create(CustomerSaveReqVO reqVO);

    /**
     * 修改客户。
     */
    void update(Long id, CustomerSaveReqVO reqVO);

    /**
     * 删除客户（逻辑删除，走 delFlag）。
     */
    void delete(Long id);

    /**
     * 放入公海（负责人置空 + 记录进入公海时间/原因）。
     */
    void putToPool(Long id, ToPoolReqVO reqVO);

    /**
     * 销售负责人下拉选项（新增/修改客户、公海分配共用）。
     */
    List<SalesOwnerRespVO> ownerOptions();

    /**
     * 关注客户（幂等，走 StarService + StarBizType.CUSTOMER）。
     */
    void star(Long id);

    /**
     * 取消关注客户（幂等）。
     */
    void unstar(Long id);

    /**
     * 导出客户（生成 Excel → 上传 OSS → 返回下载 URL），含当前过滤条件。
     */
    String export(CustomerPageReqVO reqVO);

    /**
     * 导出客户（生成 XML/SpreadsheetML → 上传 OSS → 返回下载 URL），含当前过滤条件。
     */
    String exportXml(CustomerPageReqVO reqVO);
}
