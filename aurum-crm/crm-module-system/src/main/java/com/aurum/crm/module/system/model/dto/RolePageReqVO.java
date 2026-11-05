package com.aurum.crm.module.system.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 角色分页查询。前端目前是全量取（pageSize=100），没有筛选条件 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "角色分页查询")
public class RolePageReqVO extends PageParam {

    @Schema(description = "角色名称或标识，模糊搜索")
    private String keyword;
}
