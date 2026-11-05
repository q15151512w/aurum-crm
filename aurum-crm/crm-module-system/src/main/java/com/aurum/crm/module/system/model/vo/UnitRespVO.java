package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 计量单位列表 / 新增后回读用。
 *
 * <p>字段名是**前端的叫法**（{@code id} / {@code name}），前端 {@code ProList} 把每行的
 * {@code id} 当主键、编辑弹窗直接拿列表行回填（{@code openEdit(rows[0])}），
 * 所以列表必须返回编辑表单需要的全部字段 —— 本模块就是 {@code name}。
 *
 * <p>{@code creator} 不是 {@code sys_unit} 的列：由 {@code create_by} 批量回查
 * {@code sys_user.real_name} 得来。查不到用户时为 {@code null}，
 * 而全局配置了 {@code default-property-inclusion: non_null}，该字段会被 JSON **整个省略**
 * （不是 {@code null}）—— 前端显示 {@code --}。
 */
@Data
@Schema(description = "计量单位")
public class UnitRespVO {

    @Schema(description = "单位ID（雪花ID，JSON 里是字符串，精度安全序列化）")
    private Long id;

    @Schema(description = "单位名称")
    private String name;

    @Schema(description = "单位编码")
    private String code;

    @Schema(description = "数量小数位（0~4）")
    private Integer decimalPlaces;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态：0 停用 / 1 启用")
    private Integer status;

    @Schema(description = "创建人姓名（跨表补值）")
    private String creator;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
