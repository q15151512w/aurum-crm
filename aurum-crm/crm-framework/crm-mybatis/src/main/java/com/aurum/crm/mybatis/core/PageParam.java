package com.aurum.crm.mybatis.core;

import com.aurum.crm.common.core.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

/**
 * 分页请求参数基类。
 *
 * <p>查询 DTO 继承本类即可获得分页能力：
 * <pre>{@code
 * @Data
 * @EqualsAndHashCode(callSuper = true)
 * public class CustomerPageReqVO extends PageParam {
 *     private String customerName;
 *     private Integer level;
 * }
 * }</pre>
 */
@Data
public class PageParam implements Serializable {

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    @Schema(description = "页码，从 1 开始", example = "1")
    private Integer pageNo = 1;

    @Schema(description = "每页条数，最大 500", example = "10")
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    /**
     * 转换为 MyBatis-Plus 的分页对象。
     *
     * <p>这里做参数兜底，避免前端传 {@code pageNo=0} 或负数导致 SQL 报错；
     * 上限交给 {@code PaginationInnerInterceptor.maxLimit} 兜底。
     */
    public <T> Page<T> toPage() {
        int no = (pageNo == null || pageNo < 1) ? 1 : pageNo;
        int size = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : pageSize;
        return new Page<>(no, size);
    }

    /**
     * 把 MyBatis-Plus 的 {@code Page<T>} 转成本项目的 {@link PageResult}。
     *
     * <p><b>为什么不让 Controller 直接返回 {@code Page}</b>：
     * {@code Page} 里带着 {@code orders}、{@code optimizeCountSql} 等一堆内部字段，
     * 暴露给前端既冗余又不利于后续换 ORM。
     *
     * @param page   查询结果
     * @param mapper DO -> VO 的转换函数
     */
    public static <T, R> PageResult<R> toResult(Page<T> page, Function<T, R> mapper) {
        List<R> list = page.getRecords().stream().map(mapper).toList();
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }
}
