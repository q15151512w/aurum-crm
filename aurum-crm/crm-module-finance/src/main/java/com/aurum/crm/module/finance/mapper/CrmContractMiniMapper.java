package com.aurum.crm.module.finance.mapper;

import com.aurum.crm.module.finance.model.CrmContractMiniDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 合同迷你 Mapper（财务模块只读引用 {@code crm_contract}，用于回款的跨表补值与金额校验）。
 *
 * <p>⚠️ 这里所有查询都用 {@code @InterceptorIgnore(dataPermission = "true")} 绕过数据权限：
 * 它们是「读取关联单据的客观事实」，不是「过滤列表」。若被数据权限拦截器改写，
 * 非「全部」范围的用户会查不到他人负责的合同，导致回款的合同名称 / 未回款金额为空、
 * 累计回款校验误判 —— 补值必须基于全量，不能按当前登录人的可见范围收窄。
 */
@Mapper
public interface CrmContractMiniMapper extends BaseMapperX<CrmContractMiniDO> {

    /** 行锁读合同（回款登记/编辑时防并发 + 金额校验），绕过数据权限 */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT contract_id, contract_no, contract_name, customer_id, owner_user_id, dept_id, amount, audit_status, status, del_flag FROM crm_contract WHERE contract_id = #{id} AND del_flag = 0 FOR UPDATE")
    CrmContractMiniDO selectByIdForUpdate(@Param("id") Long id);

    /** 单条补值（详情），绕过数据权限 */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT contract_id, contract_no, contract_name, customer_id, owner_user_id, dept_id, amount, audit_status, status, del_flag FROM crm_contract WHERE contract_id = #{id} AND del_flag = 0")
    CrmContractMiniDO selectByIdNoPerm(@Param("id") Long id);

    /** 批量补值（列表），绕过数据权限 */
    @InterceptorIgnore(dataPermission = "true")
    @Select("<script>SELECT contract_id, contract_no, contract_name, customer_id, owner_user_id, dept_id, amount, audit_status, status, del_flag FROM crm_contract WHERE del_flag = 0 AND contract_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<CrmContractMiniDO> selectListNoPerm(@Param("ids") Collection<Long> ids);
}
