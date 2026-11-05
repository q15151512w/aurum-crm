package com.aurum.crm.module.finance.model.convert;

import com.aurum.crm.module.finance.model.InvoiceDO;
import com.aurum.crm.module.finance.model.vo.InvoiceRespVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * 发票对象转换器（MapStruct）。
 */
@Mapper(componentModel = "spring")
public interface InvoiceConvert {

    @Mapping(source = "invoiceId", target = "id")
    @Mapping(source = "attachmentIds", target = "attachmentIds", qualifiedByName = "parseIds")
    InvoiceRespVO toRespVO(InvoiceDO invoiceDO);

    @Named("parseIds")
    default java.util.List<Long> parseIds(String value) {
        if (value == null || value.isBlank()) return java.util.List.of();
        return cn.hutool.json.JSONUtil.parseArray(value).toList(Long.class);
    }
}
