package com.aurum.crm.module.finance.mapper;

import com.aurum.crm.module.finance.model.CrmCustomerInvoiceInfoMiniDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 客户开票信息迷你 Mapper（财务模块只读引用 {@code crm_customer_invoice_info}）。
 *
 * <p>只服务「选客户 → 自动带出开票抬头」一处，故不提供增删改。
 * 正式的客户开票信息 CRUD 属于 CRM 模块，不在本次范围。
 *
 * <p>⚠️ <b>这里刻意不加 {@code @InterceptorIgnore}</b>：
 * {@code crm_customer_invoice_info} 不在数据权限拦截器的白名单里，
 * 查询本就不会被改写。照着 {@code CrmContractMiniMapper} 的样子补一个 ignore
 * 属于 cargo cult —— 会让后人以为这张表确实受权限管辖、进而以为这里绕过了什么。
 */
@Mapper
public interface CustomerInvoiceInfoMiniMapper extends BaseMapperX<CrmCustomerInvoiceInfoMiniDO> {

    /**
     * 取该客户的默认开票信息；没有默认行时退而取最新一条；一条都没有则返回 {@code null}。
     *
     * <p>「默认行」是业务概念（{@code is_default = 1}），但客户可能压根没标默认。
     * 与其返回 null 让用户手填，不如退一步用最新维护的那条 —— 同一客户的
     * 开票抬头基本只有一两个，取最近一条的猜测几乎总对，且用户看到后可以改。
     */
    @Select("""
            SELECT info_id, customer_id, invoice_title, tax_no, bank_name, bank_account,
                   invoice_addr, invoice_phone, is_default, del_flag
              FROM crm_customer_invoice_info
             WHERE customer_id = #{customerId} AND del_flag = 0
             ORDER BY is_default DESC, info_id DESC
             LIMIT 1
            """)
    CrmCustomerInvoiceInfoMiniDO selectDefaultByCustomerId(@Param("customerId") Long customerId);
}
