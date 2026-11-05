package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 计量单位选择器项（{@code GET /system/unit/simple-list}）。
 *
 * <p>只给下拉需要的字段：不返回 {@code sort} / {@code creator} / {@code createTime}，
 * 避免传输用不上的数据。
 *
 * <p><b>为什么 {id} 与 {name} 都要给</b>：产品模块的写入链路两种都支持 ——
 * 前端现状提交 {@code unitName}（服务端按名字解析），
 * 而 {@code ProductSaveReqVO.unitId} 传了则优先且更严格。
 * 下拉同时给出两者，将来前端想从「传名字」改成「传 id」时不必再改接口。
 *
 * <p>{@code decimalPlaces} 是给明细编辑器的数量精度用的（见《14》5.5）。
 */
@Data
@Schema(description = "计量单位选择器项")
public class UnitSimpleRespVO {

    @Schema(description = "单位ID")
    private Long id;

    @Schema(description = "单位名称")
    private String name;

    @Schema(description = "单位编码")
    private String code;

    @Schema(description = "数量小数位（0~4）")
    private Integer decimalPlaces;
}
