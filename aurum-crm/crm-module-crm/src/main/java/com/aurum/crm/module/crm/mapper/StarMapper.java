package com.aurum.crm.module.crm.mapper;

import com.aurum.crm.module.crm.model.StarDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 关注记录 Mapper。
 *
 * <p>保持空接口 —— 本项目约定不写 Mapper XML，查询条件一律在 Service 层用
 * {@code Wrappers.lambdaQuery()} 拼。
 */
@Mapper
public interface StarMapper extends BaseMapperX<StarDO> {
}
