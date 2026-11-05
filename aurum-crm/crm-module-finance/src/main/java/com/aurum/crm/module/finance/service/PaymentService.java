package com.aurum.crm.module.finance.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.finance.model.dto.PaymentPageReqVO;
import com.aurum.crm.module.finance.model.dto.PaymentSaveReqVO;
import com.aurum.crm.module.finance.model.dto.PaymentAuditReqVO;
import com.aurum.crm.module.finance.model.vo.PaymentRespVO;

/**
 * 回款 Service：分页查询 + 导出（Excel → OSS → URL）。
 *
 * <p>数据权限由 {@code DataPermissionInnerInterceptor} 统一改写，
 * 本模块不手写数据权限过滤（与客户模块同法）。
 */
public interface PaymentService {

    /**
     * 回款分页列表（含数据权限过滤）。
     */
    PageResult<PaymentRespVO> pageQuery(PaymentPageReqVO reqVO);

    PageResult<PaymentRespVO> auditPage(PaymentPageReqVO reqVO);

    PaymentRespVO get(Long id);

    Long create(PaymentSaveReqVO reqVO);

    void update(Long id, PaymentSaveReqVO reqVO);

    void delete(Long id);

    void submit(Long id);

    void audit(Long id, PaymentAuditReqVO reqVO);

    /**
     * 关注回款（幂等：已关注则直接返回）。
     */
    void star(Long id);

    /**
     * 取消关注回款（幂等：未关注则影响 0 行，不报错）。
     */
    void unstar(Long id);

    /**
     * 导出回款（生成 Excel → 上传 OSS → 返回下载 URL），含当前过滤条件。
     */
    String export(PaymentPageReqVO reqVO);

    /**
     * 导出回款（生成 XML/SpreadsheetML → 上传 OSS → 返回下载 URL），含当前过滤条件。
     */
    String exportXml(PaymentPageReqVO reqVO);
}
