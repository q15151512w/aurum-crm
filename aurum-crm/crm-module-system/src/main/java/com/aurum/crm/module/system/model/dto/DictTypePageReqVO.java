package com.aurum.crm.module.system.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型分页查询。
 *
 * <p>⚠️ 前端列表页（{@code ProList}）恒定带一个 {@code scope=all} —— 那是全站列表页共用的
 * 组件行为。这里**故意不声明**它：Spring 的 bean 绑定会静默忽略未声明的查询参数，
 * 正好就是「未知值不筛选」。同 {@code UnitPageReqVO} 的处理。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "字典类型分页查询")
public class DictTypePageReqVO extends PageParam {

    @Schema(description = "关键字，同时匹配字典名称与字典类型编码（空串等于不筛）", example = "客户来源")
    private String keyword;

    @Schema(description = "状态：0 停用 / 1 正常；不传 = 全部", example = "1")
    private Integer status;
}
