package com.aurum.crm.module.crm.ai.provider.deepseek;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.ai.config.AiProperties;
import com.aurum.crm.module.crm.ai.provider.AiChatRequest;
import com.aurum.crm.module.crm.ai.provider.AiMessage;
import com.aurum.crm.module.crm.ai.provider.AiModelClient;
import com.aurum.crm.module.crm.ai.provider.AiStreamChunk;
import com.aurum.crm.module.crm.ai.provider.AiToolCall;
import com.aurum.crm.module.crm.ai.provider.AiToolSpec;
import com.aurum.crm.module.crm.ai.provider.AiUsage;
import com.aurum.crm.module.crm.ai.provider.CloseableIterator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * DeepSeek 客户端（OpenAI 兼容的 {@code /chat/completions} 线格式）。
 *
 * <p><b>这是全项目唯一知道 OpenAI 响应形状的类</b> ——
 * {@code choices[0].delta.tool_calls[0].function.arguments} 这种路径只出现在这里。
 * 工具调用的分片聚合也全部收敛在本类内，上层拿到的永远是聚合好的
 * {@link AiToolCall}。想接别家模型时，照着本类再写一个即可，编排层不用动。
 */
@Slf4j
@Component
public class DeepSeekModelClient implements AiModelClient {

    /** 提供商标识，与 yml 里 {@code ai.providers} 的键一致。 */
    public static final String PROVIDER = "deepseek";

    private static final String SSE_DATA_PREFIX = "data:";
    private static final String SSE_DONE = "[DONE]";

    private final HttpClient httpClient;
    private final AiProperties properties;

    public DeepSeekModelClient(HttpClient aiHttpClient, AiProperties properties) {
        this.httpClient = aiHttpClient;
        this.properties = properties;
    }

    @Override
    public String providerKey() {
        return PROVIDER;
    }

    @Override
    public CloseableIterator<AiStreamChunk> stream(AiChatRequest request) {
        // 按【自己的】providerKey 取配置，而不是 properties.currentProvider() ——
        // 否则以后同时配了 deepseek 和 openai 时，两个客户端会抢同一份配置。
        AiProperties.Provider cfg = properties.getProviders().get(providerKey());
        if (cfg == null || !StringUtils.hasText(cfg.getApiKey())) {
            throw new ServiceException(ErrorCode.AI_NOT_CONFIGURED);
        }

        String url = trimTrailingSlash(cfg.getBaseUrl()) + "/chat/completions";
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("Authorization", "Bearer " + cfg.getApiKey())
                .header("Accept", "text/event-stream")
                // 超时覆盖「整个交换过程」，包括把流读完。模型吐完一篇十几秒是正常的，
                // 默认给到 60s；它同时也是「上游挂死」的止损线。
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .POST(HttpRequest.BodyPublishers.ofString(
                        buildPayload(request, cfg).toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<InputStream> response;
        try {
            // ⚠️ 必须用同步的 send()，绝不能用 sendAsync()：
            //    sendAsync 的回调跑在 HttpClient 自己的线程池上，而工具执行必须发生在
            //    请求线程上 —— LoginUserHolder 是 ThreadLocal，更是数据权限拦截器
            //    判断「要不要加过滤条件」的唯一依据。切了线程，拦截器会不加任何条件
            //    直接放行（见 DataPermissionInnerInterceptor），销售专员就会静默看到
            //    全公司的数据：不报错、无告警、界面完全正常。
            //    同理由此，本类也不给 HttpClient 设置 .executor(...)。
            //
            // ⚠️ 用 ofInputStream() 而不是 ofLines()：前者「拿到响应头就返回」，
            //    后者要等 body 订阅完成，流式下行为不够确定。配合 BufferedReader
            //    逐行读，增量语义明确。
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException e) {
            throw new ServiceException(ErrorCode.AI_TIMEOUT, "连接 AI 服务超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断标志，别把中断吞掉
            throw new ServiceException(ErrorCode.AI_SERVICE_UNAVAILABLE, "请求被中断");
        } catch (IOException e) {
            log.error("[AI] 调用上游失败 url={}", url, e);
            throw new ServiceException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }

        if (response.statusCode() / 100 != 2) {
            // 把上游的错误体读出来只进日志：里面可能有配额、模型名之类的内部信息，
            // 对用户没有意义，统一回「稍后重试」。
            String body = readAllQuietly(response.body());
            log.error("[AI] 上游返回非 2xx：status={}, url={}, body={}",
                    response.statusCode(), url, abbreviate(body));
            throw new ServiceException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8));
        return new DeepSeekStreamIterator(reader);
    }

    // ==================== 请求体构造 ====================

    private JSONObject buildPayload(AiChatRequest request, AiProperties.Provider cfg) {
        JSONObject payload = new JSONObject();
        payload.set("model", request.getModel() != null ? request.getModel() : cfg.getModel());
        payload.set("stream", true);
        // 不加这个的话，流式响应里**不会**返回 usage，日志表就记不到 token 消耗
        payload.set("stream_options", new JSONObject().set("include_usage", true));
        if (request.getTemperature() != null) {
            payload.set("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            payload.set("max_tokens", request.getMaxTokens());
        }
        payload.set("messages", buildMessages(request.getMessages()));
        if (request.hasTools()) {
            payload.set("tools", buildTools(request.getTools()));
        }
        return payload;
    }

    private JSONArray buildMessages(List<AiMessage> messages) {
        JSONArray array = new JSONArray();
        for (AiMessage m : messages) {
            JSONObject jm = new JSONObject();
            jm.set("role", m.getRole());
            // content 不能为 null：调工具那一轮正文是空的，但字段仍要在（传空串）
            jm.set("content", m.getContent() == null ? "" : m.getContent());

            if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                JSONArray toolCalls = new JSONArray();
                for (AiToolCall tc : m.getToolCalls()) {
                    toolCalls.add(new JSONObject()
                            .set("id", tc.getId())
                            .set("type", "function")
                            .set("function", new JSONObject()
                                    .set("name", tc.getName())
                                    .set("arguments", tc.getArguments())));
                }
                jm.set("tool_calls", toolCalls);
            }
            // 回灌工具结果时必须带 tool_call_id，且与上面的 id 配对 —— 对不上上游直接 400
            if (m.getToolCallId() != null) {
                jm.set("tool_call_id", m.getToolCallId());
            }
            array.add(jm);
        }
        return array;
    }

    private JSONArray buildTools(List<AiToolSpec> tools) {
        JSONArray array = new JSONArray();
        for (AiToolSpec t : tools) {
            array.add(new JSONObject()
                    .set("type", "function")
                    .set("function", new JSONObject()
                            .set("name", t.getName())
                            .set("description", t.getDescription())
                            .set("parameters", t.getParameters())));
        }
        return array;
    }

    // ==================== 流式解析 ====================

    /**
     * 把 SSE 行流解析成 {@link AiStreamChunk}。
     *
     * <p>工具调用的分片在这里聚合：{@code arguments} 是字符串片段，必须**拼接后**再解析；
     * {@code id} / {@code name} 只在第一片出现。聚合结果在**流结束时**作为单独一帧吐出，
     * 所以上层永远看不到碎片。
     */
    private final class DeepSeekStreamIterator implements CloseableIterator<AiStreamChunk> {

        private final BufferedReader reader;
        private final Deque<AiStreamChunk> queue = new ArrayDeque<>();
        /** index -> 该次工具调用的累积片段。用 LinkedHashMap 保持模型给的顺序。 */
        private final Map<Integer, ToolCallAcc> toolAcc = new LinkedHashMap<>();

        private String finishReason;
        private AiUsage usage;
        private boolean streamEnded;
        private boolean closed;

        DeepSeekStreamIterator(BufferedReader reader) {
            this.reader = reader;
        }

        @Override
        public boolean hasNext() {
            if (!queue.isEmpty()) {
                return true;
            }
            fill();
            return !queue.isEmpty();
        }

        @Override
        public AiStreamChunk next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return queue.poll();
        }

        /** 一直读到能产出一帧、或流真的结束。 */
        private void fill() {
            while (queue.isEmpty() && !streamEnded) {
                String line;
                try {
                    line = reader.readLine();
                } catch (IOException e) {
                    // 连接被上游掐断、或超时。已经吐出去的内容无法收回，
                    // 所以这里抛出去由编排层转成 event:error，而不是静默当正常结束。
                    close();
                    throw new ServiceException(ErrorCode.AI_SERVICE_UNAVAILABLE, "AI 服务连接中断");
                }
                if (line == null) {
                    streamEnded = true;
                    enqueueFinal();
                    break;
                }
                handleLine(line);
            }
        }

        private void handleLine(String line) {
            // SSE 的空行是帧分隔符，注释行（如 ": keep-alive"）和 event: 行直接忽略
            if (line.isEmpty() || !line.startsWith(SSE_DATA_PREFIX)) {
                return;
            }
            String data = line.substring(SSE_DATA_PREFIX.length()).trim();
            if (data.isEmpty() || SSE_DONE.equals(data)) {
                return;
            }

            JSONObject chunk;
            try {
                chunk = JSONUtil.parseObj(data);
            } catch (Exception e) {
                // 单帧解析失败不该整条流挂掉：记日志跳过，继续读后面的
                log.warn("[AI] 无法解析上游帧，已跳过：{}", abbreviate(data));
                return;
            }

            JSONObject usageObj = chunk.getJSONObject("usage");
            if (usageObj != null) {
                usage = AiUsage.of(
                        usageObj.getInt("prompt_tokens", 0),
                        usageObj.getInt("completion_tokens", 0),
                        usageObj.getInt("total_tokens", 0));
            }

            JSONArray choices = chunk.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                // 开了 include_usage 后，上游会在最后补一帧只带 usage、choices 为空的帧
                return;
            }
            JSONObject choice = choices.getJSONObject(0);

            JSONObject delta = choice.getJSONObject("delta");
            if (delta != null) {
                String content = delta.getStr("content");
                // ⚠️ 必须判空：工具调用轮上游会发 content 为空串的 delta，
                //    不过滤的话前端会冒出一堆空气泡
                if (content != null && !content.isEmpty()) {
                    queue.add(AiStreamChunk.content(content));
                }
                accumulateToolCalls(delta);
            }

            String fr = choice.getStr("finish_reason");
            if (fr != null) {
                finishReason = fr;
            }
        }

        private void accumulateToolCalls(JSONObject delta) {
            JSONArray toolCalls = delta.getJSONArray("tool_calls");
            if (toolCalls == null) {
                return;
            }
            for (int i = 0; i < toolCalls.size(); i++) {
                JSONObject tc = toolCalls.getJSONObject(i);
                int index = tc.getInt("index", 0);
                ToolCallAcc acc = toolAcc.computeIfAbsent(index, k -> new ToolCallAcc());
                if (tc.containsKey("id")) {
                    acc.id = tc.getStr("id");
                }
                JSONObject fn = tc.getJSONObject("function");
                if (fn == null) {
                    continue;
                }
                if (fn.containsKey("name")) {
                    // 用追加而不是赋值：个别上游会把函数名也拆成多片
                    acc.name.append(fn.getStr("name"));
                }
                String args = fn.getStr("arguments");
                if (args != null) {
                    // ⚠️ 这里是**字符串追加**，绝不能逐片 parse ——
                    //    每一片单独拿出来都是非法 JSON，parse 必炸
                    acc.args.append(args);
                }
            }
        }

        /** 流结束：把聚合好的工具调用与 usage 补出去。 */
        private void enqueueFinal() {
            if (!toolAcc.isEmpty()) {
                List<AiToolCall> calls = new ArrayList<>(toolAcc.size());
                for (ToolCallAcc acc : toolAcc.values()) {
                    calls.add(AiToolCall.of(acc.id, acc.name.toString(), acc.args.toString()));
                }
                queue.add(AiStreamChunk.tools(calls, finishReason));
            } else if (finishReason != null) {
                queue.add(AiStreamChunk.finished(finishReason));
            }
            if (usage != null) {
                queue.add(AiStreamChunk.usage(usage));
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                reader.close();
            } catch (IOException e) {
                log.debug("[AI] 关闭上游流时出错（忽略）：{}", e.getMessage());
            }
        }
    }

    /** 一次工具调用的累积片段。 */
    private static final class ToolCallAcc {
        private String id;
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder args = new StringBuilder();
    }

    // ==================== 小工具 ====================

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** 读干净一个流并关闭它，失败就返回空串 —— 只用于错误分支，不能再抛异常盖住原因。 */
    private static String readAllQuietly(InputStream in) {
        try (InputStream stream = in) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 500 ? text : text.substring(0, 500) + "...";
    }
}
