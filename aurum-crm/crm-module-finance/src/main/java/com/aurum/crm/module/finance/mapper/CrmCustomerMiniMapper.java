package com.aurum.crm.module.finance.mapper;

import com.aurum.crm.module.finance.model.CrmCustomerMiniDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 客户迷你 Mapper（财务模块只读引用 {@code crm_customer}，用于回款的跨表补值）。
 *
 * <p>⚠️ 同 {@link CrmContractMiniMapper}，补值查询绕过数据权限 ——
 * 读取客户名称是「客观事实」，不应按当前登录人的可见范围收窄。
 */
@Mapper
public interface CrmCustomerMiniMapper extends BaseMapperX<CrmCustomerMiniDO> {

    /** 单条补值（详情），绕过数据权限 */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT customer_id, customer_name, del_flag FROM crm_customer WHERE customer_id = #{id} AND del_flag = 0")
    CrmCustomerMiniDO selectByIdNoPerm(@Param("id") Long id);

    /** 批量补值（列表），绕过数据权限 */
    @InterceptorIgnore(dataPermission = "true")
    @Select("<script>SELECT customer_id, customer_name, del_flag FROM crm_customer WHERE del_flag = 0 AND customer_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<CrmCustomerMiniDO> selectListNoPerm(@Param("ids") Collection<Long> ids);
}
