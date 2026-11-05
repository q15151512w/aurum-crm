package com.aurum.crm.module.crm.mapper;

import com.aurum.crm.mybatis.core.BaseMapperX;
import com.aurum.crm.module.crm.model.CrmPoolRuleDO;
import org.apache.ibatis.annotations.Mapper;

/** 公海规则配置 Mapper（空接口，查询条件在 Service 层用 Wrappers 拼） */
@Mapper
public interface CrmPoolRuleMapper extends BaseMapperX<CrmPoolRuleDO> {
}
