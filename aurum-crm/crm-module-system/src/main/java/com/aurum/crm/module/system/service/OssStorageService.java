package com.aurum.crm.module.system.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * OSS 对象存储读写封装 —— <b>只负责对象本身，不碰数据库</b>。
 *
 * <p>与 {@link FileService} 的分工：
 * <ul>
 *     <li>本接口：对象 key 规则、URL 拼接、文件校验、上传与删除</li>
 *     <li>{@link FileService}：编排「上传 + 落 {@code sys_attachment}」以及失败补偿</li>
 * </ul>
 *
 * <p>实现类：{@link com.aurum.crm.module.system.service.impl.OssStorageServiceImpl}。
 */
public interface OssStorageService {

    /**
     * 上传文件到 OSS。
     *
     * @param file    上传的文件（不能为空文件）
     * @param bizType 业务类型，用于对象目录归类（如 {@code product}），可为空，空则归入 {@code common}
     * @return 对象 key（存储路径），形如 {@code crm/product/2026/09/3f2a....png}
     * @throws com.aurum.crm.common.exception.ServiceException 文件为空 / 超限 / 扩展名不在白名单 / OSS 侧失败
     */
    String upload(MultipartFile file, String bizType);

    /**
     * 上传内存中的字节内容到 OSS（导出 Excel / XML 等无原始文件的场景）。
     *
     * @param content     文件字节，不能为空
     * @param bizType     业务类型，用于对象目录归类（如 {@code export}）
     * @param contentType 对象元数据的 Content-Type（如 {@code application/vnd.ms-excel}）
     * @param extension   对象扩展名（如 {@code xlsx} / {@code xml}），可为空则不带扩展名
     * @return 对象 key（形如 {@code crm/export/2026/09/<uuid>.xlsx}）
     */
    String upload(byte[] content, String bizType, String contentType, String extension);

    /**
     * 由对象 key 拼出公网可访问 URL（bucket 为公共读，存库时直接存这个 URL）。
     */
    String buildUrl(String objectKey);

    /**
     * 由公网 URL 反解对象 key（{@link #buildUrl} 的逆操作）。
     *
     * <p>导出下载走这里：前端拿到的是 URL，要经后端流式转发到本地时，先把 URL
     * 还原成对象 key 再去 {@link #openStream}。URL 前缀不是本 bucket 时返回 {@code null}，
     * 由调用方拒绝，避免把后端当成任意 URL 的代理（SSRF 防线）。
     *
     * @param url 完整公网 URL（例 {@code https://qk-files.oss-cn-chengdu.aliyuncs.com/crm/export/...xlsx}）
     * @return 对象 key，URL 前缀不匹配时返回 {@code null}
     */
    String objectKeyOf(String url);

    /**
     * 解析上传内容的 MIME 类型：优先用浏览器给的，缺省时按扩展名推断，
     * 再不行用 {@code application/octet-stream}。
     *
     * <p>对外暴露是因为它必须是 **Content-Type 的唯一真相来源**：
     * OSS 对象元数据用它，{@code sys_attachment.file_type} 也应当用它，
     * 否则会出现「OSS 上是 image/png、库里是 null」这类不一致。
     */
    String resolveContentType(MultipartFile file);

    /**
     * 打开对象内容流（下载用）。
     *
     * <p><b>调用方负责关闭返回的流</b>（通常交给 {@code InputStreamResource}，
     * 由 Spring 在写完响应后关闭）。
     *
     * @param objectKey 对象 key
     * @throws com.aurum.crm.common.exception.ServiceException 对象不存在或取不到（如已被人工清理）
     */
    InputStream openStream(String objectKey);

    /**
     * 删除对象，<b>失败只记日志不抛异常</b>。
     *
     * <p>用于「落库失败要回滚已上传对象」这类补偿场景：补偿本身失败不应该
     * 覆盖掉真正的业务异常，也不该让接口失败。残留对象靠后续对账清理。
     */
    void deleteQuietly(String objectKey);
}
