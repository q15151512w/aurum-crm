package com.aurum.crm.module.crm.model.convert;

import com.aurum.crm.module.crm.model.LeadDO;
import com.aurum.crm.module.crm.model.RegionText;
import com.aurum.crm.module.crm.model.dto.LeadSaveReqDTO;
import com.aurum.crm.module.crm.model.vo.LeadRespVO;
import com.aurum.crm.module.crm.model.vo.LeadSimpleRespVO;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * 线索对象转换器（MapStruct）。
 *
 * <ul>
 *     <li>{@code id} / {@code name} 是给前端通用列表组件用的别名，分别取自
 *         {@code leadId} / {@code leadName}；</li>
 *     <li>{@code ownerName} 在 DO 中无对应字段，由 Service 层跨表补名；</li>
 *     <li>{@code starred} 依赖当前登录人，同样由 Service 层填充；</li>
 *     <li>{@code creator} 在 DO 中只有 ID（审计字段 {@code create_by}），姓名同样由 Service 补。</li>
 * </ul>
 *
 * <p>后三者显式 {@code ignore}，把「这些字段由谁负责」写进契约，
 * 顺带消掉 MapStruct 的 unmapped-target 警告。
 */
@Mapper(componentModel = "spring")
public interface LeadConvert {

    @Mapping(source = "leadId", target = "id")
    @Mapping(source = "leadName", target = "name")
    @Mapping(target = "ownerName", ignore = true)
    @Mapping(target = "starred", ignore = true)
    @Mapping(target = "creator", ignore = true)
    @Mapping(target = "region", ignore = true)
    @Mapping(target = "prevOwnerName", ignore = true)
    LeadRespVO toRespVO(LeadDO leadDO);

    /**
     * 把 province / city / district 三段拼成前端的 `region` 拼接串。
     *
     * <p>用 {@code @AfterMapping} 而不是 {@code @Mapping}：MapStruct 的 {@code @Mapping}
     * 是「单源 → 单目标」，没法把三个源字段合成一个目标字段。
     */
    @AfterMapping
    default void fillRegion(LeadDO source, @MappingTarget LeadRespVO target) {
        target.setRegion(RegionText.join(source.getProvince(), source.getCity(), source.getDistrict()));
    }

    /**
     * 请求 → DO。
     *
     * <p>{@code nextContactTime} 在这里显式 {@code ignore}：请求侧是 {@code String}
     * （前端清空日期时发空字符串），DO 侧是 {@code LocalDate}，交给 Service 用
     * {@code parseDateLeniently} 宽容解析 —— 与 {@code OpportunityConvert} 处理
     * {@code expectedCloseDate} 同一套写法。
     */
    @Mapping(target = "nextContactTime", ignore = true)
    LeadDO toDO(LeadSaveReqDTO reqDTO);

    @Mapping(source = "leadId", target = "id")
    @Mapping(source = "leadName", target = "name")
    @Mapping(target = "ownerName", ignore = true)
    LeadSimpleRespVO toSimpleRespVO(LeadDO leadDO);
}
