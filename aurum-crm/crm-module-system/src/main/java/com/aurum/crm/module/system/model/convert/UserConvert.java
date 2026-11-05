package com.aurum.crm.module.system.model.convert;

import com.aurum.crm.module.system.dal.dataobject.SysUserDO;
import com.aurum.crm.module.system.model.vo.UserRespVO;
import com.aurum.crm.module.system.model.vo.UserSimpleRespVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 人员对象转换器（MapStruct）。
 *
 * <p>只负责「同名字段 + 固定改名」的搬运。跨表补名（deptName / superiorName / roleName）
 * 与中文转换（gender / workStatus / accountStatus）都在 Service 层做 ——
 * 那些需要额外查库或依赖别的字段，不适合放在这里。
 */
@Mapper(componentModel = "spring")
public interface UserConvert {

    /**
     * DO → 列表/详情 VO。
     *
     * <p>两个改名：{@code userId → id}、{@code realName → name}
     * （展示口径，见 {@link UserRespVO} 的说明）。
     * 其余字段一律 ignore，由 Service 手工填。
     */
    @Mapping(source = "userId", target = "id")
    @Mapping(source = "realName", target = "name")
    @Mapping(target = "gender", ignore = true)
    @Mapping(target = "deptName", ignore = true)
    @Mapping(target = "superiorName", ignore = true)
    @Mapping(target = "roleName", ignore = true)
    @Mapping(target = "workStatus", ignore = true)
    @Mapping(target = "accountStatus", ignore = true)
    UserRespVO toRespVO(SysUserDO user);

    /** DO → 选择器项。deptName 由 Service 补 */
    @Mapping(source = "userId", target = "id")
    @Mapping(source = "realName", target = "name")
    @Mapping(target = "deptName", ignore = true)
    UserSimpleRespVO toSimpleVO(SysUserDO user);
}
