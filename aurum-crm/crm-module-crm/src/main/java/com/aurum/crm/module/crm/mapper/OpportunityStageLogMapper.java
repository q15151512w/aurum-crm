package com.aurum.crm.module.crm.mapper;

import com.aurum.crm.module.crm.model.OpportunityStageLogDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商机阶段变更记录 Mapper，对应表 {@code crm_opportunity_stage_log}。
 *
 * <p>只追加流水表，没有逻辑删除列，因此这里只用到 {@code insert} 与
 * {@code selectList}（不调用 {@code deleteById} 之类的逻辑删除方法）。
 */
@Mapper
public interface OpportunityStageLogMapper extends BaseMapperX<OpportunityStageLogDO> {
}
