package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 角色新增 / 修改请求。
 *
 * <p>⚠️ {@code dataScope} 是**中文**（"全部数据"/"自定义"/"本部门"/"本部门及以下"/"仅本人"），
 * 由 {@code DisplayEnumTranslator} 转成库里的 1~5。
 *
 * <p>⚠️ {@code deptIds} 只在 {@code dataScope == "自定义"} 时才提交 ——
 * 前端表单里这个字段带 {@code visible} 条件，不可见时**根本不会发**。
 * 所以它是可选的；切换到非自定义范围时后端会主动清空 {@code sys_role_dept}。
 */
@Data
@Schema(description = "角色新增/修改")
public class RoleSaveReqVO {

    @Schema(description = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入角色名称")
    private String name;

    /** 角色标识。程序里判权限用的字符串，唯一且建成后不可改（前端编辑态已禁用输入） */
    @Schema(description = "角色标识，如 sales", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入角色标识")
    private String roleKey;

    @Schema(description = "数据范围（中文）：全部数据/自定义/本部门/本部门及以下/仅本人",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择数据范围")
    private String dataScope;

    /** 自定义数据范围时可见的部门。仅 dataScope == "自定义" 时有意义 */
    @Schema(description = "自定义部门ID列表（仅数据范围=自定义时提交）")
    private List<Long> deptIds;

    /**
     * 所属角色组 ID，null = 未分组。
     *
     * <p>纯分组标签，不影响权限判定。前端「所属角色组」下拉会用 selector 的
     * extraProp 把选中的组 id 写到这里。
     */
    @Schema(description = "所属角色组ID（null=未分组）")
    private Long groupId;

    @Schema(description = "备注")
    private String remark;
}
