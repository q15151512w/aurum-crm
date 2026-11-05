package com.aurum.crm.module.crm.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 小助手配置，绑定 {@code application-dev.yml} 的 {@code ai.*}。
 *
 * <pre>
 * ai:
 *   enabled: true
 *   provider: deepseek
 *   providers:
 *     deepseek:
 *       base-url: https://api.deepseek.com
 *       api-key: ${DEEPSEEK_API_KEY:}
 *       model: deepseek-chat
 * </pre>
 *
 * <p><b>为什么提供商做成 Map 而不是写死 deepseek 字段</b>：需求是「接入各种大模型」，
 * 加一个 OpenAI / 通义（都是 OpenAI 兼容线格式）只需要在 yml 里多一段、改一下
 * {@code base-url} + {@code model}，Java 侧一行都不用动。
 *
 * <p>为什么用 {@code @Component} 而不是 {@code @EnableConfigurationProperties}：
 * 与 {@code OssProperties} 同一套理由 —— 启动类在 {@code com.aurum.crm}，
 * 组件扫描天然覆盖本包，少一处需要记住的注册。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /**
     * 总开关。
     *
     * <p>关掉之后接口一律返回 {@code AI_NOT_CONFIGURED(5006)}，前端据此隐藏入口 ——
     * 这是「临时不想让大家用 AI」时唯一需要改的地方，不需要动代码。
     */
    private boolean enabled = true;

    /** 当前使用的提供商，取值是 {@link #providers} 的键。 */
    private String provider = "deepseek";

    /**
     * 工具调用的最大轮数。
     *
     * <p>一次「先搜客户 → 再查该客户的合同」是两轮。给到 3 足够覆盖真实问法；
     * 再多基本是模型在绕圈，成本与时延都不可控。到达上限后最后一轮不带工具，
     * 逼模型输出自然语言而不是把空结果丢给用户。
     */
    private int maxToolRounds = 3;

    /** 单条提问的最大字符数，超出返回 {@code AI_PROMPT_TOO_LONG(5005)}。 */
    private int maxPromptChars = 4000;

    /**
     * 单个工具结果序列化后的字符上限，超出就丢行并标记 {@code truncated}。
     *
     * <p>工具返回的是真实业务数据（一页 10 条线索约 2-3KB），
     * 三四个工具叠加就能把上下文吃掉一半 —— 而模型真正需要的是「有几条、都是什么」，
     * 不是每一条的全部字段。
     */
    private int toolResultMaxChars = 8000;

    /** 与上游建立连接的超时（毫秒）。 */
    private int connectTimeoutMs = 5000;

    /**
     * 读取上游响应的超时（毫秒）。
     *
     * <p>比连接超时大得多 —— 流式响应下这个超时覆盖的是「整个流读完」，
     * 而模型吐完一篇回答要十几秒到几十秒是正常的。
     */
    private int readTimeoutMs = 60000;

    /**
     * 工具返回的字符串字段截断长度。
     *
     * <p>备注、地址这类自由文本可以很长，但对「AI 帮我看下这个客户」没有增量价值，
     * 截断能显著压缩上下文。
     */
    private int toolFieldMaxChars = 100;

    /**
     * 会话在 Redis 里的保留天数（每次读写都会刷新，即「闲置多久后过期」）。
     *
     * <p>会话内容会包含客户名、电话、金额这类业务数据，而 Redis 是**内存库**、
     * 且和 MySQL 挤在同一台远程机器上 —— 所以这个值同时也是「敏感数据在内存里
     * 停留多久」的开关，别随手调大。
     */
    private int sessionTtlDays = 7;

    /**
     * 单个会话最多保留多少条消息（超出后丢弃最早的）。
     *
     * <p>同时是两件事的上限：单个 Redis 键的体积（每条消息可能上千字）、
     * 以及每轮发给模型的上下文长度 —— 会话越长，prompt token 越多、越贵。
     */
    private int sessionMaxMessages = 50;

    /** 各提供商的连接参数，键即 {@link #provider} 的取值。 */
    private Map<String, Provider> providers = new LinkedHashMap<>();

    /** 当前生效的提供商配置；未配置时为 {@code null}。 */
    public Provider currentProvider() {
        return providers.get(provider);
    }

    /**
     * AI 当前是否可用：开关打开 **且** 当前提供商配了 api-key。
     *
     * <p>不可用时接口返回 {@code AI_NOT_CONFIGURED(5006)}，而不是让请求打到上游再失败 ——
     * 那样用户看到的是「AI 服务暂时不可用」，会误以为是网络问题而反复重试。
     */
    public boolean usable() {
        Provider p = currentProvider();
        return enabled && p != null && StringUtils.hasText(p.getApiKey());
    }

    /** 单个提供商的连接参数。 */
    @Data
    public static class Provider {

        /** API 根地址，**不带** {@code /chat/completions} 后缀（由客户端拼接）。 */
        private String baseUrl;

        /**
         * API Key。
         *
         * <p>建议走环境变量占位符 {@code ${DEEPSEEK_API_KEY:}} 而不是明文提交 ——
         * 本仓 {@code application-dev.yml} 的 DB / Redis / OSS 密钥目前都是明文，
         * 而这个文件头自己写着「不要把密码提交到仓库」。新增的这一项别再重复那个问题。
         */
        private String apiKey;

        /** 模型名，如 {@code deepseek-chat}。 */
        private String model;

        /**
         * 采样温度。
         *
         * <p>刻意偏低：这个助手的主要任务是**查数**，调工具时需要参数稳定，
         * 温度高了会把「行业=1」猜成「行业=2」。
         */
        private Double temperature = 0.3;

        /** 单次回复的最大 token 数。 */
        private Integer maxTokens = 2048;
    }
}
