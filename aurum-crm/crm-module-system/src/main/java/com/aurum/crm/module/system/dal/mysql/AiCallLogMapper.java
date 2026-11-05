package com.aurum.crm.module.system.dal.mysql;

import com.aurum.crm.module.system.dal.dataobject.AiCallLogDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 调用日志 Mapper。
 *
 * <p><b>包路径必须在本包内</b>（{@code com.aurum.crm.**.dal.mysql}），
 * 由 {@code MybatisPlusConfig} 的 {@code @MapperScan} 扫描注册，放到别的包注入不进来。
 *
 * <p>本表不在数据权限白名单里（{@code DataPermissionInnerInterceptor.SCOPED_TABLES}），
 * 所以不会自动追加 {@code owner_user_id} 之类的条件 —— 这是对的：
 * 调用日志按 {@code operator_id} 手动过滤即可，加自动条件反而会让日志查不全。
 */
@Mapper
public interface AiCallLogMapper extends BaseMapperX<AiCallLogDO> {
}
