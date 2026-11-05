package com.aurum.crm.module.system.dal.mysql;

import com.aurum.crm.mybatis.core.BaseMapperX;
import com.aurum.crm.module.system.dal.dataobject.DictTypeDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典类型 Mapper。
 *
 * <p><b>包路径必须在本包下</b>（{@code com.aurum.crm.**.dal.mysql}），
 * 由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.aurum.crm.**.dal.mysql")} 扫描注册。
 * <b>放到别的包里注入不进来</b>，启动时会报找不到 Bean。
 *
 * <p>虽然加了 {@code @Mapper} 注解，但 {@code @MapperScan} 已覆盖本包，
 * 两者取其一即可 —— 这里保留注解是为了让 IDE 能识别、避免误报。
 */
@Mapper
public interface DictTypeMapper extends BaseMapperX<DictTypeDO> {
}
