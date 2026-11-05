package com.aurum.crm.common.core;

import com.aurum.crm.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结果。
 *
 * <p>全站所有接口统一返回此结构，前端只需判断 {@code code}：
 * <ul>
 *     <li>{@code code == 0} 表示成功</li>
 *     <li>{@code code != 0} 表示业务失败，{@code message} 可直接展示给用户</li>
 * </ul>
 *
 * @param <T> 业务数据类型
 */
@Data
@Schema(description = "统一响应结果")
public class CommonResult<T> implements Serializable {

    /** 成功状态码 */
    public static final int CODE_SUCCESS = 0;

    @Schema(description = "状态码，0 表示成功", example = "0")
    private Integer code;

    @Schema(description = "提示信息", example = "操作成功")
    private String message;

    @Schema(description = "业务数据")
    private T data;

    public static <T> CommonResult<T> success() {
        return success(null);
    }

    public static <T> CommonResult<T> success(T data) {
        CommonResult<T> result = new CommonResult<>();
        result.setCode(CODE_SUCCESS);
        result.setMessage("操作成功");
        result.setData(data);
        return result;
    }

    public static <T> CommonResult<T> error(ErrorCode errorCode) {
        return error(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> CommonResult<T> error(ErrorCode errorCode, String message) {
        return error(errorCode.getCode(), message);
    }

    public static <T> CommonResult<T> error(Integer code, String message) {
        CommonResult<T> result = new CommonResult<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    /** 便于快速判断，无需在业务代码里硬编码 0 */
    public boolean isSuccess() {
        return CODE_SUCCESS == (code == null ? -1 : code);
    }
}
