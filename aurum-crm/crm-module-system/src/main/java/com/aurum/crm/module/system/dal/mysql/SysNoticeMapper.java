package com.aurum.crm.module.system.dal.mysql;

import com.aurum.crm.module.system.dal.dataobject.SysNoticeDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息通知 Mapper。
 *
 * <p>包路径必须在 {@code com.aurum.crm.**.dal.mysql} 内，由
 * {@code MybatisPlusConfig} 的 {@code @MapperScan} 扫描注册。
 *
 * <p>⚠️ {@code sys_notice} 不在数据权限白名单里（可见性由 {@code receiver_id} 决定），
 * 拦截器不会替我们加任何条件 —— <b>调用方必须自己带 {@code receiver_id} 条件</b>
 * （「我的消息」）或 {@code notice_type} 条件（「管理端」）。
 */
@Mapper
public interface SysNoticeMapper extends BaseMapperX<SysNoticeDO> {
}
