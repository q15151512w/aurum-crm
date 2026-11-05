package com.aurum.crm.module.system.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 角色组分页查询 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "角色组分页查询")
public class RoleGroupPageReqVO extends PageParam {

    @Schema(description = "角色组名称，模糊搜索")
    private String keyword;
}
