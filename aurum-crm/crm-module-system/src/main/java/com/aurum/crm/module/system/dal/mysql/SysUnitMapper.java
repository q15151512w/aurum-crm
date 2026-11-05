package com.aurum.crm.module.system.dal.mysql;

import com.aurum.crm.module.system.dal.dataobject.SysUnitDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计量单位 Mapper。
 *
 * <p><b>包路径必须在本包内</b>（{@code com.aurum.crm.**.dal.mysql}），
 * 由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.aurum.crm.**.dal.mysql")} 扫描注册。
 * <b>放到别的包里注入不进来</b>，启动时会报找不到 Bean。
 */
@Mapper
public interface SysUnitMapper extends BaseMapperX<SysUnitDO> {
}
