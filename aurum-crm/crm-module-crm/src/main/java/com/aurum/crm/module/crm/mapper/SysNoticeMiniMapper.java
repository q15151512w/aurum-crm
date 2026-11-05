package com.aurum.crm.module.crm.mapper;

import com.aurum.crm.module.crm.model.SysNoticeMiniDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息通知精简投影 Mapper，供首页看板的「消息」「公告 | 通知」使用。
 *
 * <p>没有自定义查询方法 —— 看板要的都是「按接收人 + 类型 + 时间倒序分页」这种
 * 标准的条件组合，用 {@code Wrappers.lambdaQuery} 构造即可，
 * 不需要像两个财务 Mapper 那样写 {@code SUM} 聚合。
 *
 * <p>⚠️ <b>调用方必须自己带 {@code receiver_id} 条件</b>：
 * {@code sys_notice} 不在数据权限白名单里（它的可见性由接收人决定，不该由
 * 「负责人 / 部门」决定），拦截器不会替我们加任何条件。
 *
 * <p>⚠️ 同样不继承 {@code BaseDO}，查询要自己带 {@code del_flag = 0}。
 */
@Mapper
public interface SysNoticeMiniMapper extends BaseMapperX<SysNoticeMiniDO> {
}
