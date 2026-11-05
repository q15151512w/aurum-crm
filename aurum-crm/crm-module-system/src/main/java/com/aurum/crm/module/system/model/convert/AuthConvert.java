package com.aurum.crm.module.system.model.convert;

import com.aurum.crm.module.system.dal.dataobject.SysMenuDO;
import com.aurum.crm.module.system.dal.dataobject.SysUserDO;
import com.aurum.crm.module.system.model.vo.MenuRespVO;
import com.aurum.crm.module.system.model.vo.UserInfoRespVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 认证模块对象转换器（MapStruct）。
 *
 * <p>需要 {@code ignore} 的字段都是「DO 里没有对应来源」的：
 * <ul>
 *     <li>{@code deptName / roles / permissions / menus} —— 跨表数据，由 Service 层补齐</li>
 *     <li>{@code children} —— 树形结构，由 Service 层挂接</li>
 * </ul>
 * 不 ignore 的话 MapStruct 会因为找不到来源字段而报错。
 */
@Mapper(componentModel = "spring")
public interface AuthConvert {

    @Mapping(target = "deptName", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    @Mapping(target = "menus", ignore = true)
    // gender 必须 ignore：DO 里是 Integer（0/1/2），VO 里是中文（未知/男/女），
    // 不 ignore 的话 MapStruct 会自己把 Integer 映射成 String，得到 "1" / "2" —— 编译能过、
    // 界面直接显示数字，是那种不会报错只会默默错的情况。转码由 Service 层用
    // DisplayEnumTranslator.genderToText() 做，与 UserServiceImpl 同一套口径。
    @Mapping(target = "gender", ignore = true)
    UserInfoRespVO toUserInfo(SysUserDO user);

    /**
     * 菜单 DO → 菜单节点。
     *
     * <p>{@code children} 被 ignore，但 {@link MenuRespVO} 的字段初始化器
     * 会保留空 List，所以转换结果依然带着 {@code []} 而不是 null。
     */
    @Mapping(target = "children", ignore = true)
    MenuRespVO toMenuVO(SysMenuDO menu);
}
