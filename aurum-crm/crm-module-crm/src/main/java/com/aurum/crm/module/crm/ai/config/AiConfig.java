package com.aurum.crm.module.crm.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * AI 客户端装配。
 *
 * <p><b>为什么启动时不做连通性自检</b>：与 {@code OssConfig} 同一套理由 ——
 * 那会引入一次外网调用，既拖慢启动，又让「没网 / 上游抖动」直接变成「应用起不来」。
 *
 * <p><b>但配置缺失的处理与 OSS 相反，这里刻意不 fail-fast。</b>
 * {@code OssConfig} 缺配置会让整个服务起不来，因为上传是这个 CRM 的**必备**能力；
 * AI 是**可选**能力，没配 key 只是这个小助手不能用，完全不应该拦住别人登录查客户。
 * 所以这里只打 WARN，接口层返回 {@code AI_NOT_CONFIGURED(5006)}，前端据此隐藏入口。
 */
@Slf4j
@Configuration
public class AiConfig {

    @Bean
    public HttpClient aiHttpClient(AiProperties properties) {
        if (!properties.isEnabled()) {
            log.info("[AI] ai.enabled=false，AI 小助手已关闭（接口将返回 5006）");
        } else if (!properties.usable()) {
            log.warn("[AI] 未配置 ai.providers.{}.api-key，AI 小助手暂不可用。"
                            + "设置环境变量 DEEPSEEK_API_KEY（或直接写进 yml）后重启即可启用。",
                    properties.getProvider());
        } else {
            log.info("[AI] 已启用：provider={}, model={}",
                    properties.getProvider(), properties.currentProvider().getModel());
        }

        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                // ⚠️ 刻意【不】调用 .executor(...)：
                //    JDK HttpClient 默认用自带的线程池执行响应处理。我们全程只用同步的
                //    send()，整条「调模型 → 执行工具 → 再调模型」的链路都在请求线程上跑，
                //    这样才能拿到 LoginUserHolder —— 它是 ThreadLocal，也是数据权限
                //    拦截器判断该不该加过滤条件的唯一依据。一旦切线程，拦截器会
                //    【不加任何条件】放行（见 DataPermissionInnerInterceptor），
                //    销售专员就会静默查到全公司的数据。
                .build();
    }
}
