package com.aurum.crm.common.jackson;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局配置。
 *
 * <p>做两件事：
 * <ol>
 *   <li>给 {@code Long} / {@code long} 装上 {@link JsSafeLongSerializer}，
 *       让**超出 JS 安全整数范围的雪花ID 以字符串返回**，避免前端 Number 精度丢失后
 *       回传错误的 ID。</li>
 *   <li>把 {@code LocalDateTime} 统一成 {@code yyyy-MM-dd HH:mm:ss}。</li>
 * </ol>
 *
 * <p><b>为什么放在 crm-common</b>：这两件事都是全项目共有的问题（产品、客户、线索、
 * 合同、认证……凡是用雪花ID 或带时间字段的表都一样），放在框架层一次解决，
 * 各业务模块不需要逐个加注解，也不会有人漏加。Spring Boot 的
 * {@code JacksonAutoConfiguration} 会自动收集容器里的
 * {@link Jackson2ObjectMapperBuilderCustomizer}，所以不需要额外的自动配置注册文件。
 *
 * <p>⚠️ 反序列化（请求方向）不受影响：JSON 字符串 → {@code Long} 是 Jackson 的默认能力，
 * 前端把收到的字符串 ID 原样提交回来即可。
 *
 * <p><b>为什么要单独配 {@code LocalDateTime}</b>：{@code application.yml} 里的
 * {@code spring.jackson.date-format: yyyy-MM-dd HH:mm:ss} <b>只对 {@code java.util.Date}
 * 生效，管不到 {@code LocalDateTime}</b>（Spring Boot 的经典坑）。{@code LocalDateTime}
 * 由 {@code JavaTimeModule} 处理，默认按 ISO-8601 输出成 {@code 2026-09-14T09:13:37} ——
 * 中间那个 {@code T} 会原样显示在列表页上。早先只有商机、报价单三个 VO 各自手写了
 * {@code @JsonFormat} 打补丁，线索/客户/合同/产品/看板则一直漏着，于是同一个格式问题
 * 被反复发现。这里补齐后，yml 里那句 date-format 才算名副其实。
 *
 * <p>⚠️ 序列化与反序列化**必须成对配置**：只改序列化器的话，写出去的是空格分隔，
 * 而默认反序列化器只认 ISO 的 {@code T} 分隔，往返一次就解析不回来。
 */
@Configuration
public class JacksonConfig {

    /**
     * 与 {@code application.yml} 的 {@code spring.jackson.date-format} 保持一致。
     *
     * <p>改动这里要同步改 yml —— 前者管 {@code java.util.Date}，后者管
     * {@code LocalDateTime}，两边不一致会让同一个接口的两种时间字段格式打架。
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsSafeLongCustomizer() {
        JsSafeLongSerializer serializer = new JsSafeLongSerializer();
        return builder -> {
            builder.serializerByType(Long.class, serializer);
            // 兼顾声明为基本类型 long 的字段（它们不会匹配上面那条包装类型注册）
            builder.serializerByType(Long.TYPE, serializer);
        };
    }

    /**
     * {@code LocalDateTime} ⇄ {@code yyyy-MM-dd HH:mm:ss}。
     *
     * <p>不动 {@code LocalDate}：它的 ISO 默认输出本来就是 {@code yyyy-MM-dd}，
     * 正是列表页要的样子（如 {@code nextContactTime}）。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
        return builder -> {
            builder.serializerByType(LocalDateTime.class,
                    new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
            builder.deserializerByType(LocalDateTime.class,
                    new LocalDateTimeDeserializer(DATE_TIME_FORMATTER));
        };
    }
}
