package com.aurum.crm.module.system.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志分页查询。
 *
 * <p>参数形状与 {@link LoginLogPageReqVO} 完全一致（前端两个页面共用同一个
 * {@code ProList} 组件）：{@code pageNo} / {@code pageSize} / {@code keyword}，
 * 外加固定带上的 {@code scope=all} 与 {@code _t} 时间戳。
 *
 * <p>同样**故意不声明** {@code scope} 与 {@code _t}，交给 Spring 静默忽略。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "操作日志分页查询")
public class OperationLogPageReqVO extends PageParam {

    /**
     * 关键字，同时匹配「操作人」（{@code oper_name}）与「模块」（{@code title}）。
     *
     * <p>与前端搜索框占位符「操作人/模块」一致。空串等于不筛。
     */
    @Schema(description = "关键字：操作人 / 模块（空串等于不筛）", example = "客户")
    private String keyword;
}
