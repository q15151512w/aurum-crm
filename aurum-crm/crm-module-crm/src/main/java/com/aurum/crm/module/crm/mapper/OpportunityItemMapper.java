package com.aurum.crm.module.crm.mapper;

import com.aurum.crm.module.crm.model.OpportunityItemDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商机产品明细 Mapper，对应表 {@code crm_opportunity_item}。
 *
 * <p>「整份覆盖」的更新语义靠这里实现：先 {@code delete}（逻辑删除旧行）再批量插入新行。
 *
 * <p><b>本表不在数据权限拦截器的白名单里</b>（{@code SCOPED_TABLES} 只列了主表），
 * 所以明细查询不会被追加权限条件 —— 这是有意的：明细的可见性由它的父单把关，
 * 而进到这里之前一定已经 `selectById` 过一次商机（越权会先返回 2201）。
 */
@Mapper
public interface OpportunityItemMapper extends BaseMapperX<OpportunityItemDO> {
}
