package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字典数据（字典项）新增 / 修改请求。
 *
 * <p>⚠️ {@code dictType} 在**新增时必填**、**修改时忽略**（不允许把一项从 A 字典挪到 B 字典 ——
 * 那等于删了一条又加一条，直接删了重建更清晰）。
 */
@Data
@Schema(description = "字典项新增/修改")
public class DictDataSaveReqVO {

    @Schema(description = "所属字典类型编码（新增必填；修改时忽略）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请指定所属字典类型")
    @Size(max = 100, message = "字典类型编码长度不能超过 100")
    private String dictType;

    @Schema(description = "字典标签（界面上显示的中文）", requiredMode = Schema.RequiredMode.REQUIRED, example = "官网")
    @NotBlank(message = "请输入字典标签")
    @Size(max = 100, message = "字典标签长度不能超过 100")
    private String label;

    /**
     * 字典键值。
     *
     * <p>⚠️ 是**字符串**：{@code sys_dict_data.dict_value} 是 {@code VARCHAR(100)}，
     * 而业务表里对应的字段可能是 {@code TINYINT}（如 {@code crm_lead.source}）。
     * 也就是说库里存的是 {@code '1'} 而不是 {@code 1} —— 前端取值后需要自己转类型。
     * 这个约定在《05-接口文档》的字段口径约定里有说明，不要在这里擅自改成 Integer。
     */
    @Schema(description = "字典键值（字符串形式，如 \"1\"）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotBlank(message = "请输入字典键值")
    @Size(max = 100, message = "字典键值长度不能超过 100")
    private String value;

    @Schema(description = "排序（同级内升序），不传 = 0", example = "1")
    private Integer sort;

    @Schema(description = "状态：正常 / 停用；不传 = 正常", example = "正常")
    private String status;

    @Schema(description = "是否默认项：true 表示表单下拉默认选中它", example = "false")
    private Boolean isDefault;

    @Schema(description = "备注")
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
