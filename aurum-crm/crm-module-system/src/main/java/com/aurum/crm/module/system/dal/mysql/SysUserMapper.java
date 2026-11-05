package com.aurum.crm.module.system.dal.mysql;

import com.aurum.crm.mybatis.core.BaseMapperX;
import com.aurum.crm.module.system.dal.dataobject.SysUserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 *
 * <p>保持空接口 —— 本项目约定不写 Mapper XML，查询条件一律在 Service 层用
 * {@code Wrappers.lambdaQuery()} 拼。
 *
 * <p>包路径必须在 {@code com.aurum.crm.**.dal.mysql} 下，否则
 * {@code MybatisPlusConfig} 的 {@code @MapperScan} 扫不到，启动即报 Bean 找不到。
 */
@Mapper
public interface SysUserMapper extends BaseMapperX<SysUserDO> {
}
