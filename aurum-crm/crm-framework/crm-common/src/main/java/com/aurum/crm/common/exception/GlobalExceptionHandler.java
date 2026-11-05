package com.aurum.crm.common.exception;

import com.aurum.crm.common.core.CommonResult;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>设计原则：
 * <ol>
 *     <li><b>业务异常</b>（{@link ServiceException}）—— 预期内的规则拒绝，打 WARN，返回业务错误码</li>
 *     <li><b>参数异常</b> —— 打 WARN，返回友好提示，不暴露内部细节</li>
 *     <li><b>未知异常</b> —— 打 ERROR 带完整堆栈，返回通用提示。<b>绝不把堆栈信息返回给前端</b></li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 业务异常 ====================

    @ExceptionHandler(ServiceException.class)
    public CommonResult<Void> handleServiceException(ServiceException e, HttpServletRequest request) {
        log.warn("[业务异常] {} {} | code={} msg={}",
                request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage());
        return CommonResult.error(e.getCode(), e.getMessage());
    }

    // ==================== 参数校验异常 ====================

    /** @RequestBody 上的 @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResult<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[参数校验失败] {}", msg);
        return CommonResult.error(ErrorCode.BAD_REQUEST, msg);
    }

    /** 表单绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public CommonResult<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[参数绑定失败] {}", msg);
        return CommonResult.error(ErrorCode.BAD_REQUEST, msg);
    }

    /** 方法参数上的 @Validated 校验失败（非对象参数） */
    @ExceptionHandler(ConstraintViolationException.class)
    public CommonResult<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("[参数约束失败] {}", msg);
        return CommonResult.error(ErrorCode.BAD_REQUEST, msg);
    }

    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public CommonResult<Void> handleMissingParam(MissingServletRequestParameterException e) {
        String msg = String.format("缺少必填参数：%s", e.getParameterName());
        log.warn("[缺少参数] {}", msg);
        return CommonResult.error(ErrorCode.BAD_REQUEST, msg);
    }

    /** 请求方法不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public CommonResult<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("[请求方法不支持] {}", e.getMessage());
        return CommonResult.error(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 路径/请求参数类型转换失败（如 {@code DELETE /crm/customer/undefined}）。
     *
     * <p>这类错误的根源通常是前端把 {@code undefined}/{@code null} 拼进了 URL，
     * 属于客户端问题 —— 返回 400 + 指明哪个参数非法，而不是 500「系统异常」。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public CommonResult<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String required = e.getRequiredType() == null ? "类型" : e.getRequiredType().getSimpleName();
        String msg = String.format("参数 %s 的值「%s」不是有效的 %s", e.getName(), e.getValue(), required);
        log.warn("[参数类型错误] 参数={} 值={} 期望类型={}", e.getName(), e.getValue(), required);
        return CommonResult.error(ErrorCode.BAD_REQUEST, msg);
    }

    /**
     * 请求体 JSON 解析失败。
     *
     * <p><b>为什么必须单独兜这一类</b>：{@link HttpMessageNotReadableException} 不在上面几个
     * 校验异常的继承链上 —— 不处理的话会直接落到 {@code Exception} 兜底分支，
     * 前端只看到一句「服务器内部错误」，既不知道哪个字段错了，也不知道该传什么值。
     *
     * <p>最常见的触发场景是**前后端字段口径不一致**：前端把字典字段传成中文
     * （如 {@code "source": "广告"}）而后端声明的是 {@code Integer}，
     * 或日期字符串格式不合法。这类问题的根因在客户端，返回 400 + 指明字段才有意义。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public CommonResult<Void> handleNotReadable(HttpMessageNotReadableException e,
                                                HttpServletRequest request) {
        String msg = describeNotReadable(e);
        log.warn("[请求体解析失败] {} {} | {}", request.getMethod(), request.getRequestURI(), msg);
        return CommonResult.error(ErrorCode.BAD_REQUEST, msg);
    }

    /**
     * 从 Jackson 异常里挖出「哪个字段、什么值、期望什么类型」。
     *
     * <p>用 {@code getMostSpecificCause()} 而不是 {@code getCause()}：Jackson 的异常常被
     * Spring 再包一层，直接取 cause 可能拿到包装类而不是底层的 {@code InvalidFormatException}。
     *
     * <p>挖不到字段路径时退回通用文案 —— 宁可提示笼统，也不能把内部类名暴露给前端。
     */
    private String describeNotReadable(HttpMessageNotReadableException e) {
        Throwable cause = e.getMostSpecificCause();

        if (cause instanceof InvalidFormatException ife) {
            String field = fieldPath(ife);
            String target = ife.getTargetType() == null ? "目标类型" : ife.getTargetType().getSimpleName();
            String value = String.valueOf(ife.getValue());
            return String.format("字段 %s 的值「%s」无法转换为 %s", field, value, target);
        }
        if (cause instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            return String.format("字段 %s 的值格式不正确", fieldPath(mie));
        }
        return "请求体格式不正确，无法解析";
    }

    /** 把 Jackson 的字段路径拼成 {@code customerId} / {@code items[0].price} 这样的可读串 */
    private String fieldPath(JsonMappingException e) {
        String path = e.getPath().stream()
                .map(JsonMappingException.Reference::getFieldName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("."));
        return path.isEmpty() ? "(未知字段)" : path;
    }

    // ==================== 路由不存在 ====================

    /**
     * 请求的路径既没有 Controller 匹配，也不是静态资源。
     *
     * <p><b>为什么必须单独兜住这一类</b>：Spring 6.1（Boot 3.2+）把「找不到路由」统一抛成
     * {@link NoResourceFoundException}，它不在上面任何一个异常的处理链上 —— 不处理就会落到
     * 下面的 {@code Exception} 兜底分支，带来两个后果：
     * <ol>
     *     <li><b>前端拿到 HTTP 500。</b>而 {@code src/utils/request.js} 的约定是
     *         「HTTP 200 + body.code」，500 是它**唯一**不走 {@code body.code} 的分支，
     *         会被当成「服务端崩了」弹通用错误。实际上只是 URL 拼错，改一处路径就能好 ——
     *         报错方向完全被带偏。</li>
     *     <li><b>每次都打一整条 ERROR 堆栈。</b>公网上任何扫描器乱扫路径都会命中这里；
     *         ERROR 被灌满之后，真正的服务端异常会被淹没在噪音里。</li>
     * </ol>
     *
     * <p>因此这里返回 HTTP 200 + {@code body.code=404}，与相邻的 400/403/405 处理保持一致；
     * 日志降到 WARN 且**不打堆栈** —— 路由不存在是预期内的客户端问题，不是服务故障。
     *
     * <p>同时注册 {@link NoHandlerFoundException}：它只在
     * {@code spring.mvc.throw-exception-if-no-handler-found=true} 时才抛，
     * 当前没开，一并接住是为了将来有人打开那个开关时行为不会悄悄退回 500。
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public CommonResult<Void> handleNotFound(Exception e, HttpServletRequest request) {
        log.warn("[路由不存在] {} {}", request.getMethod(), sanitize(request.getRequestURI()));
        return CommonResult.error(ErrorCode.NOT_FOUND);
    }

    /**
     * 清洗请求路径，专供日志使用。
     *
     * <p>路径来自请求行，{@code %0a}、{@code %0d} 解码后会带换行 —— 原样写进日志可以
     * 伪造出整条日志行（日志注入），把排查的人引到错误的地方。顺手截断，避免超长 URL 撑爆日志。
     *
     * <p>包级可见（而非 private）是为了让同包的测试能直接断言，与
     * {@code EmailCodeServiceImpl#maskEmail} 的处理一致。
     */
    static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("\\p{Cntrl}", "");
        return cleaned.length() <= 200 ? cleaned : cleaned.substring(0, 200) + "...";
    }

    // ==================== 客户端提前断开 ====================

    /**
     * 客户端在响应写完之前断开了连接。
     *
     * <p><b>触发条件是「客户端在服务端正在写响应体时断开」</b>，典型场景都不是故障：
     * <ul>
     *     <li>导出 / 大列表页，用户没等加载完就切走</li>
     *     <li>部署脚本的健康检查 {@code curl -m 6} 超时掐断（线上第一次就是这么出现的）</li>
     *     <li>用户点了浏览器停止、或者网络切换</li>
     * </ul>
     *
     * <p>⚠️ 实测（2026-09-15，独立实例开 DEBUG 验证）：<b>硬断 AI 小助手的 SSE 流并不会触发本异常</b>。
     * SSE 的写入是小段、间歇的，断开常常落在两次写入之间，压根不产生写失败。
     * 一开始想当然地以为「用户关 AI 页面」是主要来源，实测证明不是 —— 别再按那个思路排查。
     *
     * <p><b>为什么必须单独兜住</b>：它是 {@code IOException} 的子类，
     * 不处理就会落到下面的 {@code Exception} 兜底分支 —— 于是每次关个标签页，
     * 日志里就多一整条 ERROR 堆栈。AI 页面开关几次就能把 ERROR 刷满，
     * **真正的服务端异常被淹没**。这和「路由不存在」是同一类毛病（见 {@link #handleNotFound}）。
     *
     * <p><b>为什么用 DEBUG 而不是 WARN</b>：这不是客户端的错误，是正常的连接生命周期事件 ——
     * 与「参数传错」那种 WARN 不是一回事。生产日志级别是 {@code info}，
     * 所以默认不打；要看的时候把
     * {@code logging.level.com.aurum.crm.common.exception=debug} 打开即可。
     * 想发现「服务端变慢导致大量客户端超时」，靠的是响应时间指标，不是刷断开日志。
     *
     * <p><b>返回 void 是有意的</b>：这时连接已经没了，写什么都送不到对端；
     * 强行写响应体只会在一条坏掉的连接上再触发一次异常。
     * {@code void} 表示「已处理，不产出响应体」。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnect(AsyncRequestNotUsableException e,
                                       HttpServletRequest request) {
        log.debug("[客户端断开] {} {} | {}", request.getMethod(),
                sanitize(request.getRequestURI()), e.getMessage());
    }

    // ==================== 兜底 ====================

    /**
     * 未知异常。
     *
     * <p>这里返回给前端的是通用文案，<b>堆栈只进日志</b>——
     * 把 SQL 或类名暴露给前端是常见的安全疏漏。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public CommonResult<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("[系统异常] {} {}", request.getMethod(), request.getRequestURI(), e);
        return CommonResult.error(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
