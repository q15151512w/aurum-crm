package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 分配 / 移除角色人员。
 *
 * <p>⚠️ 移除用的是 {@code DELETE} 但**带 request body**（`{userIds:[...]}`）——
 * 这是前端既有写法，Spring MVC 的 {@code @DeleteMapping} + {@code @RequestBody} 能接住。
 */
@Data
@Schema(description = "角色人员操作")
public class RoleUserReqVO {

    @Schema(description = "用户ID列表")
    private List<Long> userIds;
}
