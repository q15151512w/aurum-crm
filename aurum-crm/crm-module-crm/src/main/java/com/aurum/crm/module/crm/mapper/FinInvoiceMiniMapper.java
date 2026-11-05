package com.aurum.crm.module.crm.mapper;

import com.aurum.crm.module.crm.model.FinInvoiceMiniDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发票精简投影 Mapper，供首页看板统计使用。
 *
 * <p>与 {@link FinPaymentMiniMapper} 同一套定位与注意事项（自己带 {@code del_flag}、
 * 数据权限由拦截器自动追加），理由见那边的注释。
 */
@Mapper
public interface FinInvoiceMiniMapper extends BaseMapperX<FinInvoiceMiniDO> {

    /**
     * 区间内「已开票」的价税合计，对应看板的「已开票金额」。
     *
     * <p>用 {@code total_amount}（价税合计）而不是 {@code amount}（不含税）——
     * 口径见《02-建表文档》3.6。
     *
     * @param start 区间起点（含）
     */
    @Select("SELECT IFNULL(SUM(total_amount), 0) FROM fin_invoice "
            + "WHERE del_flag = 0 AND status = 1 AND create_time >= #{start}")
    BigDecimal sumIssuedAmount(@Param("start") LocalDateTime start);
}
