package com.aurum.crm.module.system.dal.mysql;

import com.aurum.crm.module.system.dal.dataobject.CrmOpportunityItemMiniDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商机明细只读 Mapper（「计量单位是否被商机明细引用」计数用）。
 *
 * <p>⚠️ <b>只查不写</b>：写入口在 {@code crm-module-crm}。
 */
@Mapper
public interface CrmOpportunityItemMiniMapper extends BaseMapperX<CrmOpportunityItemMiniDO> {
}
