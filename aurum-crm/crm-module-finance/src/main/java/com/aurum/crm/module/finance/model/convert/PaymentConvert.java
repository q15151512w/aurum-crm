package com.aurum.crm.module.finance.model.convert;

import com.aurum.crm.module.finance.model.PaymentDO;
import com.aurum.crm.module.finance.model.vo.PaymentRespVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 回款对象转换器（MapStruct）。
 */
@Mapper(componentModel = "spring")
public interface PaymentConvert {

    @Mapping(source = "paymentId", target = "id")
    @Mapping(target = "starred", ignore = true)
    PaymentRespVO toRespVO(PaymentDO paymentDO);
}
