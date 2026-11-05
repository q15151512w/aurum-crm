package com.aurum.crm.common.exception;

import com.aurum.crm.common.core.CommonResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局异常处理器单元测试：参数类型转换失败必须返回 400，而不是 500。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void typeMismatch_returns400WithParamName() {
        // 前端把 undefined 拼进 URL：DELETE /crm/customer/undefined
        MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
                "undefined", Long.class, "id", null, null);

        CommonResult<Void> result = handler.handleTypeMismatch(e);

        assertThat(result.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
        assertThat(result.getMessage()).contains("id").contains("undefined").contains("Long");
    }

    @Test
    void typeMismatch_nullValue_returns400() {
        // 前端把 null 拼进 URL：PUT /crm/customer/null
        MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
                "null", Long.class, "id", null, null);

        CommonResult<Void> result = handler.handleTypeMismatch(e);

        assertThat(result.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
        assertThat(result.getMessage()).contains("id");
    }

    // ==================== 路由不存在 ====================

    @Test
    void noResourceFound_returns404_notServerError() {
        // 前端拼错了接口路径：GET /no/such/route
        NoResourceFoundException e =
                new NoResourceFoundException(HttpMethod.GET, "no/such/route");

        CommonResult<Void> result = handler.handleNotFound(e, request("GET", "/no/such/route"));

        assertThat(result.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
        // 回归点：修复前这里会落到 Exception 兜底分支返回 500，前端当成「服务端崩了」
        assertThat(result.getCode())
                .as("路由不存在必须是 404，不能是 500")
                .isNotEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
    }

    @Test
    void notFound_doesNotLeakPathOrInternals() {
        // 响应体只给通用文案，不回显路径、不暴露类名
        NoResourceFoundException e =
                new NoResourceFoundException(HttpMethod.GET, "no/such/route");

        CommonResult<Void> result = handler.handleNotFound(e, request("GET", "/no/such/route"));

        assertThat(result.getMessage()).isEqualTo(ErrorCode.NOT_FOUND.getMessage());
        assertThat(result.getMessage()).doesNotContain("Exception").doesNotContain("/no/such/route");
    }

    @Test
    void sanitize_stripsControlChars() {
        // %0a 解码后带换行，原样写日志可以伪造出整条日志行
        assertThat(GlobalExceptionHandler.sanitize("/a\nb\rc"))
                .isEqualTo("/abc");
    }

    @Test
    void sanitize_truncatesLongPath() {
        String longPath = "/" + "x".repeat(500);

        String cleaned = GlobalExceptionHandler.sanitize(longPath);

        assertThat(cleaned).hasSizeLessThan(220).endsWith("...");
    }

    @Test
    void sanitize_handlesNull() {
        assertThat(GlobalExceptionHandler.sanitize(null)).isEmpty();
    }

    // ==================== 客户端提前断开 ====================

    @Test
    void clientDisconnect_isHandledAndProducesNoBody() {
        // 真实触发场景：导出 / 大列表页，用户在响应还在写的时候切走。
        // （⚠️ 不是「关掉 AI 页面」—— 实测硬断 SSE 流不会触发本异常。）
        AsyncRequestNotUsableException e = new AsyncRequestNotUsableException(
                "ServletOutputStream failed to flush: java.io.IOException: Connection reset by peer");

        // 处理器返回 void（连接已经没了，不产出响应体），所以这里只能断言「调用它不抛异常」。
        // 「它确实被 Spring 接住、而不是落到 Exception 兜底分支」这一条，反射断言会写得很脆，
        // 改为在部署后打一条真实请求验证（日志里出现 DEBUG 的 [客户端断开] 而非 ERROR 堆栈）。
        handler.handleClientDisconnect(e, request("GET", "/ai/chat/stream"));
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRequestURI(uri);
        return req;
    }
}
