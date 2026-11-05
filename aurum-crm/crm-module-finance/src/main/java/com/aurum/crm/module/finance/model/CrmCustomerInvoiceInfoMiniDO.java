package com.aurum.crm.module.finance.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 客户开票信息迷你 DO，只读，对应表 {@code crm_customer_invoice_info}。
 *
 * <p>只用于「选客户 → 自动带出开票抬头」这一个场景，故只映射需要的列。
 *
 * <p>⚠️ <b>刻意不继承 {@code BaseDO}</b>：同一张表在本模块被当只读快照源使用，
 * 不需要 {@code update_by/update_time} 的自动填充语义；继承反而会让 MyBatis-Plus
 * 在 select 里多带列。{@code del_flag} 确有该列，用 {@code @TableLogic} 声明，
 * 让软删的行自动被过滤掉（照 {@link CrmCustomerMiniDO}）。
 *
 * <p>该表<b>不在</b>数据权限拦截器的 {@code SCOPED_TABLES} 白名单里，因此查询
 * 天然不受数据权限影响，无需 {@code @InterceptorIgnore}。
 */
@Data
@TableName("crm_customer_invoice_info")
public class CrmCustomerInvoiceInfoMiniDO {

    /** 主键 */
    @TableId
    private Long infoId;

    /** 客户ID */
    private Long customerId;

    /** 开票名称(抬头) */
    private String invoiceTitle;

    /** 纳税人识别号 */
    private String taxNo;

    /** 开户行 */
    private String bankName;

    /** 银行账号 */
    private String bankAccount;

    /** 开票地址 */
    private String invoiceAddr;

    /** 开票电话 */
    private String invoicePhone;

    /** 是否默认(0否 1是) */
    private Integer isDefault;

    @TableLogic
    private Integer delFlag;
}
