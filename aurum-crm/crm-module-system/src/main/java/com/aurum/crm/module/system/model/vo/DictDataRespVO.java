package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典项。
 *
 * <p>也是 {@code GET /system/dict-data/batch} 的返回元素 —— 前端缓存字典后，
 * 用 {@code label} / {@code value} 构造下拉选项，{@code listClass} 决定标签染色。
 */
@Data
@Schema(description = "字典项")
public class DictDataRespVO {

    @Schema(description = "字典项ID（雪花ID，精度安全序列化后 JSON 里是字符串）")
    private Long id;

    @Schema(description = "所属字典类型编码")
    private String type;

    @Schema(description = "字典标签（界面上显示的中文）")
    private String label;

    /** ⚠️ 字符串形式，如 {@code "1"}；业务表里可能存的是 TINYINT，前端按需转换 */
    @Schema(description = "字典键值（字符串形式）")
    private String value;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态：正常 / 停用")
    private String status;

    @Schema(description = "是否默认项")
    private Boolean isDefault;

    @Schema(description = "表格回显样式（el-tag 的 type，如 success / danger）")
    private String listClass;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
