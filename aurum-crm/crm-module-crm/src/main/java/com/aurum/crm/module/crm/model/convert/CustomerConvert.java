package com.aurum.crm.module.crm.model.convert;

import com.aurum.crm.module.crm.model.CustomerDO;
import com.aurum.crm.module.crm.model.RegionText;
import com.aurum.crm.module.crm.model.dto.CustomerSaveReqVO;
import com.aurum.crm.module.crm.model.vo.CustomerRespVO;
import com.aurum.crm.module.crm.model.vo.CustomerSimpleRespVO;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * 客户对象转换器（MapStruct）。
 *
 * <ul>
 *     <li>{@code id} / {@code name} 是给前端通用列表组件用的别名，分别取自
 *         {@code customerId} / {@code customerName}；</li>
 *     <li>{@code ownerName} 在 DO 中无对应字段，由 Service 层跨表补名；</li>
 *     <li>{@code starred} 依赖当前登录人，同样由 Service 层填充；</li>
 *     <li>{@code region} 是 province/city/district 三段拼出来的派生字段。</li>
 * </ul>
 *
 * <p>后三者显式 {@code ignore}，把「这些字段由谁负责」写进契约，
 * 顺带消掉 MapStruct 的 unmapped-target 警告。
 */
@Mapper(componentModel = "spring")
public interface CustomerConvert {

    @Mapping(source = "customerId", target = "id")
    @Mapping(source = "customerName", target = "name")
    @Mapping(target = "ownerName", ignore = true)
    @Mapping(target = "starred", ignore = true)
    @Mapping(target = "region", ignore = true)
    CustomerRespVO toRespVO(CustomerDO customerDO);

    /**
     * 把 province / city / district 三段拼成前端的 `region` 拼接串。
     *
     * <p>用 {@code @AfterMapping} 而不是 {@code @Mapping}：后者是「单源 → 单目标」，
     * 没法把三个源字段合成一个目标字段。
     */
    @AfterMapping
    default void fillRegion(CustomerDO source, @MappingTarget CustomerRespVO target) {
        target.setRegion(RegionText.join(source.getProvince(), source.getCity(), source.getDistrict()));
    }

    CustomerDO toDO(CustomerSaveReqVO reqVO);

    @Mapping(source = "customerId", target = "id")
    @Mapping(source = "customerName", target = "name")
    @Mapping(target = "ownerName", ignore = true)
    CustomerSimpleRespVO toSimpleRespVO(CustomerDO customerDO);
}
