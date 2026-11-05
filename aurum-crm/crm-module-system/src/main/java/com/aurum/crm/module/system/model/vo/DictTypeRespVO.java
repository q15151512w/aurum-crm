package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典类型列表 / 回读。
 *
 * <p>字段名是**前端列表页的叫法**（{@code id} / {@code name} / {@code type} / {@code status} /
 * {@code remark}）—— {@code useFormDialog} 直接拿列表行回填编辑表单，
 * 所以列表必须返回表单需要的全部字段。
 *
 * <p>{@code status} 是**中文标签**（{@code 正常} / {@code 停用}），不是 0/1：
 * 前端那列直接 `value === '正常'` 比较并染色，与其它状态机字段一致
 * （见 {@code DictTypeSaveReqVO} 的说明）。
 */
@Data
@Schema(description = "字典类型")
public class DictTypeRespVO {

    @Schema(description = "字典ID（雪花ID，精度安全序列化后 JSON 里是字符串）")
    private Long id;

    @Schema(description = "字典名称")
    private String name;

    @Schema(description = "字典类型编码（程序取值用的 key）")
    private String type;

    @Schema(description = "状态：正常 / 停用")
    private String status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
