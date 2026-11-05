package com.aurum.crm.module.crm.mapper;

import com.aurum.crm.module.crm.model.ProductMiniDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品精简投影 Mapper，供报价单明细取「分类快照 / 兜底单位 / 成本价」。
 *
 * <p>{@link ProductMiniDO} 不含 {@code delFlag}，所以这里的查询**不会**被
 * {@code @TableLogic} 追加 {@code del_flag = 0} —— 有意为之，
 * 产品下架/删除后历史报价单的毛利仍要算得出来。
 */
@Mapper
public interface ProductMiniMapper extends BaseMapperX<ProductMiniDO> {
}
