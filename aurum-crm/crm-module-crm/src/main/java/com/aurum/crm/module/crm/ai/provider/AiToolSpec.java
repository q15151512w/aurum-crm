package com.aurum.crm.module.crm.ai.provider;

import lombok.Data;

import java.util.Map;

/**
 * 发给模型的工具声明（OpenAI {@code tools[].function} 的结构）。
 *
 * <p>由 {@code AiTool} 实现类自己描述，编排层原样透传给上游 ——
 * 所以这里用「Map 形式的 JSON Schema」而不是自定义结构，
 * 免得每接一个模型都要翻译一遍参数描述。
 */
@Data
public class AiToolSpec {

    /** 工具名，全局唯一。 */
    private String name;

    /** 给模型看的用途说明 —— 写得越清楚，模型选错工具的概率越低。 */
    private String description;

    /**
     * 参数的 JSON Schema（{@code {"type":"object","properties":{...},"required":[...]}}）。
     *
     * <p>注意 {@code required} 要显式声明：不声明的话模型倾向于少传参数，
     * 而我们的工具层对缺失参数只能取默认值，结果就是「它以为自己查了、其实查的是全部」。
     */
    private Map<String, Object> parameters;

    public static AiToolSpec of(String name, String description, Map<String, Object> parameters) {
        AiToolSpec spec = new AiToolSpec();
        spec.name = name;
        spec.description = description;
        spec.parameters = parameters;
        return spec;
    }
}
