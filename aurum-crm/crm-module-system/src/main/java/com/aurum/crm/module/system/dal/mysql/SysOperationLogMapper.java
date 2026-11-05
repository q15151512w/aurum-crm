package com.aurum.crm.module.system.dal.mysql;

import com.aurum.crm.module.system.dal.dataobject.SysOperationLogDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper。
 *
 * <p>包路径必须在 {@code com.aurum.crm.**.dal.mysql} 下（{@code MybatisPlusConfig} 的
 * {@code @MapperScan} 只扫这个前缀），否则启动时注入不进来。
 *
 * <p><b>不受数据权限影响</b>：{@code DataPermissionInnerInterceptor} 只对
 * {@code crm_*} / {@code fin_*} 业务表生效，日志表不在白名单里。
 */
@Mapper
public interface SysOperationLogMapper extends BaseMapperX<SysOperationLogDO> {
}
