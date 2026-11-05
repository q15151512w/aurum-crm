package com.aurum.crm.module.system.dal.mysql;

import com.aurum.crm.module.system.dal.dataobject.SysLoginLogDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 Mapper。
 *
 * <p><b>包路径必须在本包内</b>（{@code com.aurum.crm.**.dal.mysql}），
 * 由 {@code MybatisPlusConfig} 的 {@code @MapperScan} 扫描注册 ——
 * 放到别的包里注入不进来，启动时会报找不到 Bean。
 *
 * <p><b>不受数据权限影响</b>：{@code DataPermissionInnerInterceptor} 的表白名单里
 * 只有业务表（{@code crm_*} / {@code fin_*}），日志表不在其中，
 * 所以这里的查询不会被自动追加部门/归属人条件。
 * 权限由 {@code @SaCheckPermission("system:loginLog:list")} 单独把关。
 */
@Mapper
public interface SysLoginLogMapper extends BaseMapperX<SysLoginLogDO> {
}
