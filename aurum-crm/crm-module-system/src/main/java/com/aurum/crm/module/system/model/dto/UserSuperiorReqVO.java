package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设置直属上级，对应 {@code PUT /system/user/{id}/superior}。
 *
 * <p>前端还会传一个 {@code superiorName}，但那只用于它本地的 mock 回写，
 * 真实请求**只发 superiorId**；姓名以库里的为准，不接收。
 */
@Data
@Schema(description = "设置直属上级")
public class UserSuperiorReqVO {

    /** 上级用户 ID。传 null 表示清空直属上级 */
    @Schema(description = "上级用户ID（null 表示清空）")
    private Long superiorId;
}
