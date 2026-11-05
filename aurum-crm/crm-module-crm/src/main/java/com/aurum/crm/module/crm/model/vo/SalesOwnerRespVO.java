package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 销售负责人下拉选项，用于客户新增/修改、公海分配的「负责人」选择器。
 *
 * <p>只查拥有销售角色（{@code sales_director} / {@code sales}）的在职用户。
 */
@Data
@Schema(description = "销售负责人选项")
public class SalesOwnerRespVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "所属部门名称")
    private String deptName;
}
