package com.aurum.crm.module.system.core.log;

import com.aurum.crm.module.system.model.OperationBusinessType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志的补充说明注解，标在 Controller 的<b>写接口方法</b>上。
 *
 * <p><b>⚠️ 这个注解是可选的，不标也会记日志。</b>
 * {@code OperationLogAspect} 的切点是「controller 包下的 POST / PUT / DELETE 方法」，
 * 自动覆盖全部写接口 —— 漏标不会漏记，这正是刻意设计的：
 * 手工给 72 个方法逐个加注解，漏一个不会报错、只会静默少一条日志，反而更不可靠。
 *
 * <p><b>那这个注解什么时候用</b>：自动推导不准的地方。切面会从上下文推导：
 * <ul>
 *     <li>{@code title}（模块）← Controller 类上的 Swagger {@code @Tag(name = "...")}，
 *         例如「客户管理」「计量单位」</li>
 *     <li>{@code businessType}（操作）← HTTP 动词：POST→新增 / PUT→修改 / DELETE→删除</li>
 * </ul>
 * 但有些接口的语义与动词对不上，典型如：
 * <pre>{@code
 * // POST 但语义是「审核」，不是「新增」
 * @Log(title = "合同", businessType = OperationBusinessType.UPDATE)
 * @PostMapping("/{id}/audit")
 * public CommonResult<Boolean> audit(@PathVariable Long id) { ... }
 * }</pre>
 *
 * <p>用法约定：
 * <ul>
 *     <li>只填 {@code title} 时，{@code businessType} 仍按 HTTP 动词推导</li>
 *     <li>显式填了 {@code businessType}（且不是 {@code OTHER}）时以注解为准</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {

    /**
     * 模块标题。留空（默认）表示「用 Controller 上 {@code @Tag(name)} 的值」。
     */
    String title() default "";

    /**
     * 业务类型。
     *
     * <p>默认 {@link OperationBusinessType#OTHER}，它的含义是「没指定」——
     * 此时切面按 HTTP 动词推导。所以这里<b>没法</b>用它来强制表达「其他」，
     * 但「其他」本来就是推导不出来时的兜底值，不影响使用。
     */
    OperationBusinessType businessType() default OperationBusinessType.OTHER;
}
