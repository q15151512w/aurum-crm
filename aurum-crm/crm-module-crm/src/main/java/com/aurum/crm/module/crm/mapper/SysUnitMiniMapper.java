package com.aurum.crm.module.crm.mapper;

import com.aurum.crm.module.crm.model.SysUnitMiniDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计量单位精简投影 Mapper（只读），用于产品列表 / 详情补「单位名称」。
 *
 * <p>不写 {@code sys_unit}，单位维护属于 system 模块（{@code /system/unit}）。
 */
@Mapper
public interface SysUnitMiniMapper extends BaseMapperX<SysUnitMiniDO> {
}
