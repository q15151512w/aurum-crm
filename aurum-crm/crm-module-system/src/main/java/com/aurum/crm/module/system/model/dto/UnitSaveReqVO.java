package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 计量单位新增 / 修改请求，字段名与前端的表单模型一致（新增与编辑共用一套）。
 *
 * <p><b>与前端的两处对应关系</b>：
 * <ul>
 *     <li>{@code name} → {@code unit_name}：前端<b>唯一</b>可编辑的字段（页面上没有编码/小数位/排序/状态的输入框），
 *         必填、≤32、全局唯一（重名返回 {@code 1503}）</li>
 *     <li>{@code code} → {@code unit_code}：前端<b>不传</b>（表单里没这个输入框），
 *         所以服务端缺省生成 {@code U + 雪花ID}；显式传了就用传入值（重复返回 {@code 1502}）</li>
 * </ul>
 *
 * <p><b>{@code remark} 会被静默忽略</b>：{@code sys_unit} 表没有 {@code remark} 列
 * （详见《14-计量单位模块接口文档》1.2）。这里保留字段是为了**兼容前端提交体、不报 400**，
 * 服务端不落库、读接口也不返回。
 */
@Data
@Schema(description = "计量单位新增/修改")
public class UnitSaveReqVO {

    @Schema(description = "单位名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "台")
    @NotBlank(message = "请输入计量单位名称")
    @Size(max = 32, message = "计量单位名称长度不能超过 32")
    private String name;

    @Schema(description = "单位编码，不传则服务端生成（U+雪花ID）", example = "SET")
    @Size(max = 32, message = "单位编码长度不能超过 32")
    private String code;

    @Schema(description = "数量小数位：0~4，缺省 0（与明细 quantity DECIMAL(15,4) 对齐）", example = "0")
    @Min(value = 0, message = "数量小数位不能小于 0")
    @Max(value = 4, message = "数量小数位不能超过 4")
    private Integer decimalPlaces;

    @Schema(description = "排序，缺省 0", example = "6")
    @Min(value = 0, message = "排序不能小于 0")
    @Max(value = 65535, message = "排序不能超过 65535")
    private Integer sort;

    @Schema(description = "状态：0 停用 / 1 启用，缺省 1", example = "1")
    @Min(value = 0, message = "状态只能是 0 停用 / 1 启用")
    @Max(value = 1, message = "状态只能是 0 停用 / 1 启用")
    private Integer status;

    /** 接收但<b>不落库</b>（sys_unit 无此列），仅为兼容前端提交体 */
    @Schema(description = "备注（⚠️ 表里没有这一列，接收后丢弃）")
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
