package com.aurum.crm.module.system.dal.mysql;

import com.aurum.crm.mybatis.core.BaseMapperX;
import com.aurum.crm.module.system.dal.dataobject.DictDataDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典数据 Mapper。
 *
 * <p>包路径必须在本包下（{@code com.aurum.crm.**.dal.mysql}），由 {@code MybatisPlusConfig}
 * 的 {@code @MapperScan} 扫描注册，放到别的包注入不进来。
 *
 * <p>本表不在数据权限白名单里（{@code DataPermissionInnerInterceptor.SCOPED_TABLES}）——
 * 这是对的：字典是**全局共享**的配置数据，不属于任何人，不该被行级权限过滤掉。
 */
@Mapper
public interface DictDataMapper extends BaseMapperX<DictDataDO> {
}
