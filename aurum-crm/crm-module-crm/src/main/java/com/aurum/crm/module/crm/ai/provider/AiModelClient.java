package com.aurum.crm.module.crm.ai.provider;

/**
 * 大模型客户端 —— 编排层只认这个接口，不认任何具体厂商。
 *
 * <p><b>加新模型的代价</b>：
 * <ul>
 *   <li>OpenAI / 通义（DashScope 兼容模式）等 OpenAI 线格式的 —— 在 yml 的
 *       {@code ai.providers} 下加一段、改 {@code base-url} 与 {@code model} 即可，
 *       复用同一个实现，Java 侧零改动</li>
 *   <li>Claude 这类线格式不同的 —— 新写一个实现类，把它的响应形状翻译成
 *       {@link AiStreamChunk}，编排层同样零改动</li>
 * </ul>
 *
 * <p><b>实现约定（必须遵守）</b>：
 * <ol>
 *   <li>{@link #stream} 必须是**同步、惰性**的：返回的迭代器由调用线程消费，
 *       <b>绝不能切换线程</b>（不要用 {@code sendAsync()}）。AI 的整条链路要跑在
 *       请求线程上才能拿到 {@code LoginUserHolder}，否则数据权限拦截器会静默放行全部数据。</li>
 *   <li>工具调用的分片聚合必须在实现内部做完，对上层只暴露聚合好的 {@link AiToolCall}。</li>
 *   <li>上游返回非 2xx、或响应体解析不了时，抛 {@code ServiceException}
 *       （错误码用 {@code AI_SERVICE_UNAVAILABLE} / {@code AI_TIMEOUT}）。</li>
 * </ol>
 */
public interface AiModelClient {

    /** 提供商标识，与 yml 里 {@code ai.providers} 的键一致，如 {@code deepseek}。 */
    String providerKey();

    /**
     * 发起一次流式对话。
     *
     * <p>返回的迭代器持有上游 HTTP 连接，调用方**必须**用 try-with-resources 关闭 ——
     * 包括中途异常/用户中断的情况。
     */
    CloseableIterator<AiStreamChunk> stream(AiChatRequest request);
}
