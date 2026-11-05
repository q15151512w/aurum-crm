package com.aurum.crm.module.system.dal.mysql;

import com.aurum.crm.module.system.dal.dataobject.CrmProductMiniDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品只读 Mapper（「计量单位是否被产品引用」计数用）。
 *
 * <p>⚠️ <b>只查不写</b>：{@code crm_product} 的写入口在 {@code crm-module-crm}。
 * 这里只做 {@code selectCount}，不 insert / update / delete。
 */
@Mapper
public interface CrmProductMiniMapper extends BaseMapperX<CrmProductMiniDO> {
}
