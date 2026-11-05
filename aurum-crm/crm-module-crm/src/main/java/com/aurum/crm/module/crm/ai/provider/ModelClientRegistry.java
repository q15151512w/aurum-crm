package com.aurum.crm.module.crm.ai.provider;

import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.ai.config.AiProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型客户端注册表：按 {@code ai.provider} 选出当前该用哪个实现。
 *
 * <p>用构造注入 {@code List<AiModelClient>} 让 Spring 把实现全部塞进来 ——
 * 加一个新模型只需要新建一个 {@code @Component}，**不用改本类**。
 * 重名直接启动失败，免得两个实现悄悄抢同一个 provider 键。
 */
@Component
public class ModelClientRegistry {

    private final Map<String, AiModelClient> byKey;
    private final AiProperties properties;

    public ModelClientRegistry(List<AiModelClient> clients, AiProperties properties) {
        Map<String, AiModelClient> map = new LinkedHashMap<>();
        for (AiModelClient client : clients) {
            if (map.putIfAbsent(client.providerKey(), client) != null) {
                throw new IllegalStateException("AI 提供商重复注册：" + client.providerKey());
            }
        }
        this.byKey = Collections.unmodifiableMap(map);
        this.properties = properties;
    }

    /**
     * 当前生效的客户端。
     *
     * <p>配了一个没注册的 provider（yml 写错字）时抛 {@code AI_NOT_CONFIGURED} 而不是
     * NPE —— 这类错配置应该在日志里说得清楚，而不是变成 500。
     */
    public AiModelClient current() {
        AiModelClient client = byKey.get(properties.getProvider());
        if (client == null) {
            throw new ServiceException(ErrorCode.AI_NOT_CONFIGURED,
                    "未注册的 AI 提供商：" + properties.getProvider());
        }
        return client;
    }
}
