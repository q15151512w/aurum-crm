package com.aurum.crm.module.system.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * OSS 客户端装配。
 *
 * <p><b>为什么启动时不做连通性自检</b>：那会引入一次外网调用，既拖慢启动，
 * 又让"没网/OSS 抖动"直接变成"应用起不来"。连通性交给集成测试
 * （{@code FileUploadIT} 会真实上传并读回），运行期问题在调用时暴露。
 *
 * <p>但**配置缺失必须 fail-fast**：{@code OSSClientBuilder.build(null, ...)}
 * 只会抛一个难以定位的 {@code IllegalArgumentException}，
 * 所以这里显式校验，把问题直接指到 yml 的哪一项。
 */
@Slf4j
@Configuration
public class OssConfig {

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(OssProperties properties) {
        requireText(properties.getEndpoint(), "oss.endpoint");
        requireText(properties.getAccessKeyId(), "oss.accessKeyId");
        requireText(properties.getAccessKeySecret(), "oss.accessKeySecret");
        requireText(properties.getBucketName(), "oss.bucketName");

        OSS client = new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());
        log.info("[OSS] 客户端初始化完成：bucket={}, endpoint={}",
                properties.getBucketName(), properties.getEndpoint());
        return client;
    }

    private void requireText(String value, String key) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(String.format(
                    "缺少 OSS 配置项 %s，请检查 crm-server/src/main/resources/application-dev.yml 的 oss 配置块", key));
        }
    }
}
