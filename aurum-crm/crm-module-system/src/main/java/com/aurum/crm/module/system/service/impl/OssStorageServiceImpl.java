package com.aurum.crm.module.system.service.impl;

import cn.hutool.core.util.IdUtil;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.system.config.OssProperties;
import com.aurum.crm.module.system.service.OssStorageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.time.LocalDate;
import java.util.Locale;

/**
 * OSS 存储实现。
 *
 * <p><b>对象 key 规则</b>（刻意不用原始文件名）：
 * <pre>
 *   {dir}{bizTypeOrCommon}/{yyyy}/{MM}/{uuid}.{ext}
 *   例： crm/product/2026/09/3f2a9c1b8d4e4f6a9c0d1e2f3a4b5c6d.png
 * </pre>
 * 为什么不用原始文件名：中文、空格、emoji、超长、以及 {@code ../../} 这类路径穿越，
 * 全都会在 OSS 上变成难排查的问题；uuid 命名天然无冲突、无非法字符。
 * 原始文件名只在 {@code sys_attachment.file_name} 里保留给人看。
 */
@Slf4j
@Service
public class OssStorageServiceImpl implements OssStorageService {

    /** bizType 缺省归类 */
    private static final String COMMON_BIZ_TYPE = "common";

    /** bizType 清洗后的最大长度，与 {@code sys_attachment.biz_type VARCHAR(32)} 对齐 */
    private static final int MAX_BIZ_TYPE_LENGTH = 32;

    /** 扩展名最大长度（超过视为不合法，丢弃扩展名） */
    private static final int MAX_EXT_LENGTH = 10;

    /** 推断不出类型时的兜底 MIME */
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    @Resource
    private OSS ossClient;
    @Resource
    private OssProperties ossProperties;

    // ==================== 上传 ====================

    @Override
    public String upload(MultipartFile file, String bizType) {
        String extension = extensionOf(file == null ? null : file.getOriginalFilename());
        validate(file);

        String objectKey = buildObjectKey(bizType, extension);
        String bucketName = ossProperties.getBucketName();

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(resolveContentType(file));
        // 注意：这里【不设置】对象级 ACL —— bucket 本身是公共读，
        // 单独给对象设 ACL 在 bucket 策略收紧时会报 AccessDenied，反而制造意外。

        try (InputStream inputStream = file.getInputStream()) {
            ossClient.putObject(bucketName, objectKey, inputStream, metadata);
        } catch (IOException e) {
            log.error("[OSS] 读取上传流失败：objectKey={}", objectKey, e);
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, "文件上传失败，请稍后重试");
        } catch (OSSException | ClientException e) {
            // OSS 侧的错误细节（含 RequestId）只进日志，不返回给前端，避免泄漏存储结构
            log.error("[OSS] 上传失败：bucket={}, objectKey={}", bucketName, objectKey, e);
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, "文件上传失败，请稍后重试");
        }
        log.info("[OSS] 上传成功：bucket={}, objectKey={}, size={}B", bucketName, objectKey, file.getSize());
        return objectKey;
    }

    @Override
    public String upload(byte[] content, String bizType, String contentType, String extension) {
        if (content == null || content.length == 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        int maxSizeMb = ossProperties.getMaxSizeMb() == null ? 0 : ossProperties.getMaxSizeMb();
        long maxBytes = maxSizeMb * 1024L * 1024L;
        if (maxBytes > 0 && content.length > maxBytes) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, String.format(
                    "文件大小 %.2fMB 超过上限 %dMB", content.length / 1024.0 / 1024.0, maxSizeMb));
        }

        String normalizedExtension = StringUtils.hasText(extension)
                ? extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "") : null;
        String objectKey = buildObjectKey(bizType, normalizedExtension);
        String bucketName = ossProperties.getBucketName();

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(StringUtils.hasText(contentType)
                ? contentType : DEFAULT_CONTENT_TYPE);
        // 同 upload(MultipartFile)：不设置对象级 ACL，依赖 bucket 公共读

        try (ByteArrayInputStream in = new ByteArrayInputStream(content)) {
            ossClient.putObject(bucketName, objectKey, in, metadata);
        } catch (IOException e) {
            // ByteArrayInputStream.close() 是 no-op，理论上不会到这里；兜底统一按 OSS 失败处理
            log.error("[OSS] 读取上传流失败：objectKey={}", objectKey, e);
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, "文件上传失败，请稍后重试");
        } catch (OSSException | ClientException e) {
            log.error("[OSS] 上传失败：bucket={}, objectKey={}", bucketName, objectKey, e);
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, "文件上传失败，请稍后重试");
        }
        log.info("[OSS] 上传成功：bucket={}, objectKey={}, size={}B", bucketName, objectKey, content.length);
        return objectKey;
    }

    // ==================== URL ====================

    @Override
    public String buildUrl(String objectKey) {
        return resolveUrlPrefix() + "/" + objectKey;
    }

    @Override
    public String objectKeyOf(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String prefix = resolveUrlPrefix();
        if (!url.startsWith(prefix + "/") && !url.startsWith(prefix)) {
            return null;
        }
        String key = url.substring(prefix.length());
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        return StringUtils.hasText(key) ? key : null;
    }

    // ==================== 下载 ====================

    @Override
    public InputStream openStream(String objectKey) {
        try {
            return ossClient.getObject(ossProperties.getBucketName(), objectKey).getObjectContent();
        } catch (OSSException | ClientException e) {
            // 库里还有记录、对象却没了（多半被人工清理或 bucket 换过）——
            // 这是可预期的业务失败，不该以 500 的形式抛给前端
            log.warn("[OSS] 取对象失败：bucket={}, objectKey={}", ossProperties.getBucketName(), objectKey);
            throw new ServiceException(ErrorCode.ATTACHMENT_NOT_EXISTS, "附件文件已不存在（可能已被清理）");
        }
    }

    // ==================== 删除 ====================

    @Override
    public void deleteQuietly(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            ossClient.deleteObject(ossProperties.getBucketName(), objectKey);
            log.info("[OSS] 已删除对象：{}", objectKey);
        } catch (Exception e) {
            // 刻意吞掉：补偿删除失败不应该覆盖真正的业务异常，也不该让接口失败
            log.warn("[OSS] 删除对象失败（不影响业务流程，留待对账清理）：objectKey={}", objectKey, e);
        }
    }

    // ==================== 内部方法 ====================

    /** 上传前校验：非空、大小 */
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        int maxSizeMb = ossProperties.getMaxSizeMb() == null ? 0 : ossProperties.getMaxSizeMb();
        long maxBytes = maxSizeMb * 1024L * 1024L;
        if (maxBytes > 0 && file.getSize() > maxBytes) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, String.format(
                    "文件大小 %.2fMB 超过上限 %dMB", file.getSize() / 1024.0 / 1024.0, maxSizeMb));
        }
    }

    /**
     * 拼对象 key：{@code {dir}{bizType}/{yyyy}/{MM}/{uuid}.{ext}}。
     */
    private String buildObjectKey(String bizType, String extension) {
        LocalDate today = LocalDate.now();
        StringBuilder objectKey = new StringBuilder()
                .append(normalizeDir())
                .append(sanitizeBizType(bizType))
                .append('/')
                .append(String.format("%04d/%02d/", today.getYear(), today.getMonthValue()))
                .append(IdUtil.fastSimpleUUID());
        if (extension != null) {
            objectKey.append('.').append(extension);
        }
        return objectKey.toString();
    }

    /**
     * 目录规范化：去掉首部与尾部的斜杠后再补一个尾部 {@code /}。
     * 这样 yml 里写 {@code crm}、{@code /crm}、{@code crm/} 得到同样结果，不会出现 {@code //}。
     */
    /**
     * 目录规范化：去掉首尾斜杠后再补一个尾部 {@code /}。
     *
     * <p>默认值只在 {@link OssProperties#getDir()} 上声明一处（{@code crm/}），
     * 这里不再自带常量，避免两处默认值各说各话。
     * 显式配成空的含义是**不要目录前缀**（对象直接落在 {@code product/2026/09/...}）。
     */
    private String normalizeDir() {
        String dir = ossProperties.getDir() == null ? "" : ossProperties.getDir().trim();
        dir = dir.replaceAll("^/+", "").replaceAll("/+$", "");
        return dir.isEmpty() ? "" : dir + "/";
    }

    /**
     * bizType 清洗：只保留字母数字、下划线、连字符。
     *
     * <p>它不是安全边界之外的输入——{@code bizType} 会拼进对象路径，
     * 放行 {@code ../} 或 {@code /} 会把对象写到预期目录之外。
     * 清洗后为空则归入 {@code common}。
     */
    private String sanitizeBizType(String bizType) {
        if (!StringUtils.hasText(bizType)) {
            return COMMON_BIZ_TYPE;
        }
        String cleaned = bizType.trim().replaceAll("[^A-Za-z0-9_-]", "");
        if (cleaned.isEmpty()) {
            return COMMON_BIZ_TYPE;
        }
        return cleaned.length() > MAX_BIZ_TYPE_LENGTH
                ? cleaned.substring(0, MAX_BIZ_TYPE_LENGTH)
                : cleaned;
    }

    /**
     * 扩展名归一化：小写、只留字母数字、限制长度；不合法则返回 {@code null}（对象不带扩展名）。
     */
    private String extensionOf(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return null;
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return null;
        }
        String extension = originalFilename.substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        if (extension.isEmpty() || extension.length() > MAX_EXT_LENGTH) {
            return null;
        }
        return extension;
    }

    /**
     * 访问 URL 前缀。
     *
     * <p>⚠️ 必须兼容 endpoint 带协议头的情况：endpoint 可能是
     * {@code oss-cn-chengdu.aliyuncs.com}，也可能是 {@code https://oss-cn-chengdu.aliyuncs.com}。
     * 直接拼 {@code "https://" + bucket + "." + endpoint} 在后者会得到
     * {@code https://qk-files.https://oss-...} 这种废 URL，所以先拆出 scheme 与 host。
     */
    private String resolveUrlPrefix() {
        if (StringUtils.hasText(ossProperties.getUrlPrefix())) {
            return trimTrailingSlash(ossProperties.getUrlPrefix().trim());
        }
        String endpoint = ossProperties.getEndpoint().trim();
        String scheme = "https://";
        String host = endpoint;
        int schemeIndex = endpoint.indexOf("://");
        if (schemeIndex > 0) {
            scheme = endpoint.substring(0, schemeIndex + 3);
            host = endpoint.substring(schemeIndex + 3);
        }
        return trimTrailingSlash(scheme + ossProperties.getBucketName() + "." + host);
    }

    private String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    /**
     * 解析上传内容的 MIME 类型：优先用浏览器给的，缺省时按扩展名推断，
     * 再不行用 {@code application/octet-stream}。
     *
     * <p><b>这是 Content-Type 的唯一真相来源</b>：OSS 对象的元数据用它，
     * {@code sys_attachment.file_type} 也用它。之前后者直接取
     * {@code file.getContentType()}，导致浏览器没给类型时出现
     * 「OSS 上是 image/png、库里是 null」的不一致。
     */
    @Override
    public String resolveContentType(MultipartFile file) {
        String extension = extensionOf(file == null ? null : file.getOriginalFilename());
        String contentType = file == null ? null : file.getContentType();
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        if (extension != null) {
            String guessed = URLConnection.guessContentTypeFromName("file." + extension);
            if (StringUtils.hasText(guessed)) {
                return guessed;
            }
        }
        return DEFAULT_CONTENT_TYPE;
    }
}
