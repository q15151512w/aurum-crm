package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部门新增 / 修改请求（新增与编辑共用一套）。
 *
 * <p>字段与 {@code sys_dept} 表对应；父部门（{@code parentId}）用于维护
 * 树的 {@code ancestors} 前缀（新增时由服务端计算，前端不必传 ancestors）。
 */
@Data
@Schema(description = "部门新增/修改")
public class DeptSaveReqVO {

    @Schema(description = "父部门ID（0 为顶级）", example = "0")
    private Long parentId;

    @NotBlank(message = "请输入部门名称")
    @Size(max = 64, message = "部门名称长度不能超过 64")
    @Schema(description = "部门名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "销售部")
    private String deptName;

    @Min(value = 1, message = "部门类型只能是 1 集团 / 2 部门 / 3 组")
    @Max(value = 3, message = "部门类型只能是 1 集团 / 2 部门 / 3 组")
    @Schema(description = "类型：1 集团 / 2 部门 / 3 组，缺省 2", example = "2")
    private Integer type;

    @Schema(description = "部门负责人用户 ID", example = "1")
    private Long leaderUserId;

    @Size(max = 64, message = "部门编码长度不能超过 64")
    @Schema(description = "部门编码", example = "SALES")
    private String deptCode;

    @Size(max = 32, message = "联系电话长度不能超过 32")
    @Schema(description = "联系电话", example = "010-66668888")
    private String phone;

    @Size(max = 64, message = "邮箱长度不能超过 64")
    @Schema(description = "邮箱", example = "sales@aurum.com")
    private String email;

    @Min(value = 0, message = "排序不能小于 0")
    @Max(value = 65535, message = "排序不能超过 65535")
    @Schema(description = "显示排序，缺省 0", example = "6")
    private Integer sort;

    @Min(value = 0, message = "状态只能是 0 停用 / 1 正常")
    @Max(value = 1, message = "状态只能是 0 停用 / 1 正常")
    @Schema(description = "状态：0 停用 / 1 正常，缺省 1", example = "1")
    private Integer status;
}
