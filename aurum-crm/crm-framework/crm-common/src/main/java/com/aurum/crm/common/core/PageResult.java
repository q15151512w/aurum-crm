package com.aurum.crm.common.core;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应结果。
 *
 * <p>字段设计参考前端表格组件（Element Plus 的 el-pagination + el-table）的常见需求。
 */
@Data
@Schema(description = "分页结果")
public class PageResult<T> implements Serializable {

    @Schema(description = "数据列表")
    private List<T> list;

    @Schema(description = "总记录数", example = "1024")
    private Long total;

    @Schema(description = "当前页码，从 1 开始", example = "1")
    private Integer pageNo;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize;

    public PageResult() {
    }

    public PageResult(List<T> list, Long total, Integer pageNo, Integer pageSize) {
        this.list = list;
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }

    public static <T> PageResult<T> empty(Integer pageNo, Integer pageSize) {
        return new PageResult<>(Collections.emptyList(), 0L, pageNo, pageSize);
    }
}
