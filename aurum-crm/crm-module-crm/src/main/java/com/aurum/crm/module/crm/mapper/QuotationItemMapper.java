package com.aurum.crm.module.crm.mapper;

import com.aurum.crm.module.crm.model.QuotationItemDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报价单明细 Mapper，对应表 {@code crm_quotation_item}。
 *
 * <p>「整份覆盖」的更新语义靠这里实现：先 {@code deleteById}（逻辑删除旧行）再批量插入新行，
 * 所以新插入的行主键一定与旧行不同（{@code ASSIGN_ID} 雪花ID）。
 */
@Mapper
public interface QuotationItemMapper extends BaseMapperX<QuotationItemDO> {
}
