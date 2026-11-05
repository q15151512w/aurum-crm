package com.aurum.crm.common.exception;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>Service 层遇到「业务规则不允许」的情况时抛出，由
 * {@link GlobalExceptionHandler} 统一转成 {@code CommonResult} 返回。
 *
 * <p>使用示例：
 * <pre>{@code
 * if (customer.getOwnerUserId() != null) {
 *     throw new ServiceException(ErrorCode.CUSTOMER_ALREADY_OWNED);
 * }
 * // 需要自定义提示语时：
 * throw new ServiceException(ErrorCode.CONCURRENT_OPERATION, "该客户已被 " + name + " 领取");
 * }</pre>
 *
 * <p><b>注意</b>：这类异常是「可预期的业务失败」，不打 ERROR 日志、不告警；
 * 代码 bug 导致的异常（NPE 等）交给 {@code Exception} 分支处理并打 ERROR 日志。
 */
@Getter
public class ServiceException extends RuntimeException {

    private final Integer code;

    public ServiceException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public ServiceException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public ServiceException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 业务异常不需要堆栈——它表达的是「预期内的规则拒绝」，不是 bug。
     * 覆写此方法可显著降低抛异常的开销（大循环里抛异常时差异明显）。
     */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
