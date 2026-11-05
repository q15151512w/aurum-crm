package com.aurum.crm.module.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OSS（阿里云对象存储）配置，绑定 {@code application-dev.yml} 的 {@code oss.*}。
 *
 * <pre>
 * oss:
 *   endpoint: oss-cn-chengdu.aliyuncs.com
 *   accessKeyId: xxx
 *   accessKeySecret: xxx
 *   bucketName: qk-files
 * </pre>
 *
 * <p><b>只有上面 4 项是必填的</b>，其余属性都有代码默认值 ——
 * 日常不需要改 yml；只有「换自定义域名 / 调上传上限 / 改对象目录」这类部署诉求才需要加配置。
 *
 * <p><b>文件类型（扩展名）校验不在这里</b>：那属于业务侧职责
 * （例如产品图片只允许 jpg/jpeg/png/webp/gif，就写在产品服务里），
 * 放一个全局白名单只会让不同业务互相牵制。
 *
 * <p>为什么用 {@code @Component} 而不是 {@code @EnableConfigurationProperties}：
 * 启动类在 {@code com.aurum.crm}，组件扫描天然覆盖本包，少一处需要记住的注册。
 */
@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    /** OSS 接入点，如 {@code oss-cn-chengdu.aliyuncs.com}（也可以带 {@code https://} 前缀，代码会兼容） */
    private String endpoint;

    /** 访问密钥 ID */
    private String accessKeyId;

    /** 访问密钥 Secret */
    private String accessKeySecret;

    /** Bucket 名称，如 {@code qk-files} */
    private String bucketName;

    /**
     * 对象目录前缀，默认 {@code crm/}。
     *
     * <p>最终对象路径形如 {@code crm/product/2026/09/<uuid>.png}。
     * 首尾斜杠由代码规范化，写 {@code crm}、{@code /crm}、{@code crm/} 都一样；
     * <b>显式配成空</b>表示不要目录前缀（对象直接落在 {@code product/2026/09/...}）。
     *
     * <p>这是该默认值的**唯一声明处**，业务代码里不要再写一份常量。
     */
    private String dir = "crm/";

    /**
     * 访问 URL 前缀（自定义域名 / CDN），默认按
     * {@code https://{bucketName}.{endpoint}} 自动拼接。
     *
     * <p>只有当 bucket 绑了自定义域名时才需要配这一项。
     */
    private String urlPrefix;

    /**
     * 单文件大小上限（MB），默认 10。
     *
     * <p>刻意小于 Spring 的 {@code spring.servlet.multipart.max-file-size}（50MB）：
     * 这样 10-50MB 的超限会由我们的校验返回 **400 + 明确提示**，
     * 而不是撞上 multipart 的 {@code MaxUploadSizeExceededException}（当前全局异常处理器
     * 没有该分支，会退化成 500）。
     */
    private Integer maxSizeMb = 10;
}
