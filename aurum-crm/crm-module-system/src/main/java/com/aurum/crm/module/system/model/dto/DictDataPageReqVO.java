package com.aurum.crm.module.system.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据分页查询（总是按某个字典类型查）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "字典数据分页查询")
public class DictDataPageReqVO extends PageParam {

    @Schema(description = "字典类型编码，如 crm_source", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dictType;

    @Schema(description = "关键字，同时匹配标签与键值（空串等于不筛）", example = "官网")
    private String keyword;

    @Schema(description = "状态：0 停用 / 1 正常；不传 = 全部", example = "1")
    private Integer status;
}
