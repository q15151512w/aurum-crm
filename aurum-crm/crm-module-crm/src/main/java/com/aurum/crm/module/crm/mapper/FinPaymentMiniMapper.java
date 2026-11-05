package com.aurum.crm.module.crm.mapper;

import com.aurum.crm.module.crm.model.FinPaymentMiniDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 回款精简投影 Mapper，供首页看板统计使用。
 *
 * <p>与 {@code FinPaymentMiniDO} 同一套定位：只读统计，不进入 finance 的 CRUD 链路。
 *
 * <p>⚠️ <b>不继承 {@code BaseDO}，所以没有 {@code @TableLogic}</b> ——
 * 每条语句都要自己写 {@code del_flag = 0}。
 *
 * <p>⚠️ <b>数据权限由拦截器自动追加</b>：{@code fin_payment} 在
 * {@code DataPermissionInnerInterceptor.SCOPED_TABLES} 白名单里，
 * 下面这些语句（包括手写的 {@code @Select}）在 {@code beforeQuery} 阶段
 * 都会被解析并追加行级条件，所以看到的金额受当前登录人的数据范围约束。
 */
@Mapper
public interface FinPaymentMiniMapper extends BaseMapperX<FinPaymentMiniDO> {

    /**
     * 区间内「已审核通过」的回款总额，对应看板的「已收款金额」。
     *
     * <p>{@code IFNULL(..., 0)}：没有任何回款时 {@code SUM} 返回 {@code NULL}，
     * 映射到 {@code BigDecimal} 就是 null，前端会显示成 {@code --} 而不是 {@code 0} ——
     * 那不是「没有数据」的意思，所以在这里兜成 0。
     *
     * @param start 区间起点（含），由 Service 按 {@code range} 算好
     */
    @Select("SELECT IFNULL(SUM(amount), 0) FROM fin_payment "
            + "WHERE del_flag = 0 AND audit_status = 2 AND create_time >= #{start}")
    BigDecimal sumApprovedAmount(@Param("start") LocalDateTime start);
}
