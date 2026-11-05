package com.aurum.crm.module.crm.ai.sse;

import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 手写 SSE 帧。
 *
 * <p><b>为什么不用 {@code SseEmitter}</b>：{@code SseEmitter} 会把请求交给
 * Spring 的异步处理链，后续的模型回调跑在**容器线程**上。而工具执行必须发生在
 * 请求线程上 —— {@code LoginUserHolder} 是 ThreadLocal，更是数据权限拦截器
 * 判断「要不要加过滤条件」的唯一依据。切了线程拦截器会静默放行全部数据
 * （见 {@code DataPermissionInnerInterceptor}）。所以这里全程同步写。
 *
 * <p><b>帧格式</b>（与前端 {@code api/ai.js} 严格对应）：
 * <pre>
 * event: delta\n
 * data: {"content":"客户"}\n
 * \n
 * </pre>
 * 用**两个 LF** 分隔帧，不是 CRLF —— 前端按 {@code \n\n} 切。
 *
 * <p><b>每次写完必须 flush</b>：Servlet 的响应缓冲区默认 8KB，
 * 不 flush 的话打字机效果会被吃掉，变成「全部生成完一次性出现」。
 */
public class SseWriter {

    /** 多久没有输出就补一个心跳，防止「首 token 前的等待」被中间设备掐断连接。 */
    private static final long PING_INTERVAL_MS = 15_000;

    private final PrintWriter writer;
    private long lastWriteAt;

    /**
     * 构造即提交响应头。
     *
     * <p>⚠️ 执行之后 HTTP 状态码就锁死 200 了 —— 所以**所有校验必须在这之前做完**
     * （权限、参数、配置检查），否则用户会拿到一个 200 的流，里面装着错误。
     */
    public SseWriter(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        // no-transform 同样重要：它告诉中间代理不要压缩/改写响应体，
        // 否则代理会缓冲整个流再转发，打字机效果直接消失
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        // nginx 专用：不加这个，nginx 默认会缓冲，前端只能等到最后才看到内容
        response.setHeader("X-Accel-Buffering", "no");
        this.writer = response.getWriter();
        this.lastWriteAt = System.currentTimeMillis();
    }

    /** 写一帧。 */
    public void event(String name, Map<String, Object> data) throws IOException {
        writer.write("event: " + name + "\n");
        // 用 JSON 承载 data：换行、引号都由 JSON 转义处理掉，
        // 不会出现「消息里的换行把一帧劈成两帧」的经典 SSE 事故
        writer.write("data: " + JSONUtil.toJsonStr(data) + "\n\n");
        writer.flush();
        lastWriteAt = System.currentTimeMillis();
    }

    /**
     * 开场帧：告知前端本次用的模型，以及**本轮的会话 ID**。
     *
     * <p>sessionId 必须在这里回传：首次提问前端没有 id（服务端新建），
     * 或者前端手里的 id 已过期（服务端另建了一个）—— 这两种情况前端都只能靠
     * 这一帧才知道该用哪个 id 继续下一轮。漏了它，第二句话就会被当成新会话。
     */
    public void meta(String model, String sessionId) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model", model);
        data.put("sessionId", sessionId);
        event("meta", data);
    }

    /** 正文增量。 */
    public void delta(String content) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", content);
        event("delta", data);
    }

    /**
     * 工具调用状态，前端据此显示「正在查询客户…」这类胶囊。
     *
     * @param status {@code running} 或 {@code done}
     */
    public void tool(String name, String status) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("status", status);
        event("tool", data);
    }

    /** 正常结束。 */
    public void done(String finishReason, Integer promptTokens, Integer completionTokens, Integer totalTokens)
            throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("finishReason", finishReason);
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("promptTokens", promptTokens);
        usage.put("completionTokens", completionTokens);
        usage.put("totalTokens", totalTokens);
        data.put("usage", usage);
        event("done", data);
    }

    /**
     * 流中途出错。
     *
     * <p>注意这时状态码已经是 200 了，没法再改成 500 —— 错误只能靠这个事件传出去。
     */
    public void error(int code, String message) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", code);
        data.put("message", message);
        event("error", data);
    }

    /**
     * 距上次输出超过阈值就补一个心跳。
     *
     * <p>不做成「定时无条件发」：那会在正常输出时也插入一堆无意义帧。
     * 真正需要心跳的是两段静默期 —— 首 token 之前的等待，以及工具执行期间。
     */
    public void pingIfIdle() throws IOException {
        if (System.currentTimeMillis() - lastWriteAt > PING_INTERVAL_MS) {
            event("ping", Map.of());
        }
    }

    public void close() {
        try {
            writer.close();
        } catch (Exception ignored) {
            // 客户端已断开时 close 会抛，没有补救余地，也不值得再报错
        }
    }
}
