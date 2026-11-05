package com.aurum.crm.module.system.model.convert;

import com.aurum.crm.module.system.dal.dataobject.SysRoleDO;
import com.aurum.crm.module.system.model.vo.RoleRespVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 角色对象转换器（MapStruct）。
 *
 * <p>只做同名字段搬运 + 两处改名（{@code roleId→id}、{@code roleName→name}）；
 * {@code dataScope} 的中文转换与 {@code userCount} 的实时统计都在 Service 层做。
 */
@Mapper(componentModel = "spring")
public interface RoleConvert {

    @Mapping(source = "roleId", target = "id")
    @Mapping(source = "roleName", target = "name")
    @Mapping(target = "dataScope", ignore = true)
    @Mapping(target = "userCount", ignore = true)
    RoleRespVO toRespVO(SysRoleDO role);
}
