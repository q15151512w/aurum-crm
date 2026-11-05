package com.aurum.crm.module.system.service.impl;

import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.system.dal.dataobject.AttachmentDO;
import com.aurum.crm.module.system.dal.mysql.AttachmentMapper;
import com.aurum.crm.module.system.model.dto.FileUploadReqVO;
import com.aurum.crm.module.system.model.vo.AttachmentDownloadRespVO;
import com.aurum.crm.module.system.model.vo.FileUploadRespVO;
import com.aurum.crm.module.system.service.FileService;
import com.aurum.crm.module.system.service.OssStorageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 文件上传编排。
 *
 * <p><b>执行顺序与失败补偿（本类最关键的一段）</b>：
 * <pre>
 *   ① 校验入参
 *   ② 上传 OSS                                → objectKey
 *   ③ 需要落库吗？（bizType + bizId 都在才落）
 *        是 → insert sys_attachment ─失败→ 补偿删除 OSS 对象，再把异常抛出去
 *        否 → 直接返回（临时上传，如产品图片）
 * </pre>
 *
 * <p><b>为什么顺序不能反</b>：先落库再上传，一旦上传失败就会留下
 * 「库里有记录、OSS 里没对象」的脏行，前端拿到 url 打开是坏的，而且没有补偿时机。
 * 反过来则是「上传成功、落库失败」，此时对象还在，可以主动删掉。
 *
 * <p><b>⚠️ 事务管不了 OSS</b>：{@code @Transactional} 只能回滚数据库；
 * 已经传到 OSS 的对象必须靠 {@code catch} 里的 {@code deleteQuietly} 手动补偿。
 * 这一点容易漏，漏了就会慢慢攒出一堆无人引用的孤儿对象。
 */
@Slf4j
@Service
public class FileServiceImpl implements FileService {

    /** 附件分类缺省值，与 {@code sys_attachment.attachment_type} 的 DDL 默认值一致 */
    private static final int DEFAULT_ATTACHMENT_TYPE = 4;

    /** 合法附件分类（字典 crm_attachment_type） */
    private static final Set<Integer> VALID_ATTACHMENT_TYPES = Set.of(1, 2, 3, 4);

    /** {@code sys_attachment.file_name VARCHAR(255)} */
    private static final int MAX_FILE_NAME_LENGTH = 255;

    /** {@code sys_attachment.file_type VARCHAR(64)} */
    private static final int MAX_FILE_TYPE_LENGTH = 64;

    @Resource
    private OssStorageService ossStorageService;
    @Resource
    private AttachmentMapper attachmentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadRespVO upload(MultipartFile file, FileUploadReqVO reqVO) {
        FileUploadReqVO request = reqVO == null ? new FileUploadReqVO() : reqVO;
        validateBinding(request);

        // ① 上传 OSS
        String objectKey = ossStorageService.upload(file, request.getBizType());

        FileUploadRespVO respVO = new FileUploadRespVO();
        respVO.setObjectKey(objectKey);
        respVO.setUrl(ossStorageService.buildUrl(objectKey));
        respVO.setFileName(resolveFileName(file, objectKey));
        respVO.setFileSize(file == null ? null : file.getSize());
        // MIME 走 OSS 层的统一解析：这样库里 file_type 与对象元数据的 Content-Type 永远一致
        respVO.setFileType(truncate(ossStorageService.resolveContentType(file), MAX_FILE_TYPE_LENGTH));

        // ② 临时上传（产品图片这种场景）不落附件表，避免产生无人引用的行
        if (!isBound(request) && !Integer.valueOf(2).equals(request.getAttachmentType())) {
            log.info("[文件上传] 临时上传（未绑定业务）：objectKey={}", objectKey);
            return respVO;
        }

        try {
            AttachmentDO attachment = buildAttachment(request, respVO);
            attachmentMapper.insert(attachment);
            respVO.setAttachmentId(attachment.getAttachmentId());
        } catch (RuntimeException e) {
            // 见类注释：数据库回滚不了 OSS 对象，必须手动补偿
            ossStorageService.deleteQuietly(objectKey);
            log.error("[文件上传] 附件落库失败，已回滚 OSS 对象：objectKey={}", objectKey, e);
            throw e;
        }
        return respVO;
    }

    // ==================== 下载 / 删除 ====================

    @Override
    public AttachmentDownloadRespVO download(Long id) {
        AttachmentDO attachment = getAttachmentDO(id);

        AttachmentDownloadRespVO respVO = new AttachmentDownloadRespVO();
        respVO.setFileName(attachment.getFileName());
        respVO.setFileSize(attachment.getFileSize());
        respVO.setContentType(attachment.getFileType());
        // InputStreamResource 包住 OSS 的流；由 Controller 写完响应后关闭
        respVO.setResource(new InputStreamResource(ossStorageService.openStream(attachment.getFilePath())));
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AttachmentDO attachment = getAttachmentDO(id);

        // 先删库（逻辑删除）……
        attachmentMapper.deleteById(id);
        // ……再尽力删对象。顺序不能反：见 FileService#delete 的说明
        if (StringUtils.hasText(attachment.getFilePath())) {
            ossStorageService.deleteQuietly(attachment.getFilePath());
        }
    }

    // ==================== 内部方法 ====================

    private AttachmentDO getAttachmentDO(Long id) {
        AttachmentDO attachment = attachmentMapper.selectById(id);
        if (attachment == null) {
            throw new ServiceException(ErrorCode.ATTACHMENT_NOT_EXISTS);
        }
        return attachment;
    }

    /** 业务绑定参数校验 */
    private void validateBinding(FileUploadReqVO request) {
        boolean hasBizType = StringUtils.hasText(request.getBizType());
        boolean hasBizId = request.getBizId() != null;
        if (hasBizType != hasBizId) {
            // 只传一个几乎总是调用方写错：要么以为落了库，要么以为算临时上传
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "bizType 与 bizId 必须同时提供，或同时不提供");
        }
        Integer attachmentType = request.getAttachmentType();
        if (attachmentType != null && !VALID_ATTACHMENT_TYPES.contains(attachmentType)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "附件分类只能是 1合同扫描件 / 2发票PDF / 3营业执照 / 4其他");
        }
    }

    private boolean isBound(FileUploadReqVO request) {
        return StringUtils.hasText(request.getBizType()) && request.getBizId() != null;
    }

    private AttachmentDO buildAttachment(FileUploadReqVO request, FileUploadRespVO respVO) {
        AttachmentDO attachment = new AttachmentDO();
        attachment.setBizType(request.getBizType().trim());
        attachment.setBizId(request.getBizId());
        attachment.setFileName(respVO.getFileName());
        // file_path 存对象 key（不是 URL）：删对象、排查问题都要用它
        attachment.setFilePath(respVO.getObjectKey());
        attachment.setFileUrl(respVO.getUrl());
        attachment.setFileSize(respVO.getFileSize());
        attachment.setFileType(respVO.getFileType());
        attachment.setAttachmentType(request.getAttachmentType() == null
                ? DEFAULT_ATTACHMENT_TYPE
                : request.getAttachmentType());
        // uploadBy 不是审计字段（createBy 才是），必须显式赋值；无登录上下文时为 null
        attachment.setUploadBy(LoginUserHolder.getUserId());
        // createBy / createTime / updateBy / updateTime / delFlag 由 BaseDO + DefaultDBFieldHandler 填充，不要手动赋值
        return attachment;
    }

    /**
     * 原始文件名。
     *
     * <p>部分浏览器/客户端会把完整路径带上来（如 {@code C:\Users\x\a.png}），
     * 这里只取最后一段；拿不到就回退成对象 key 的文件名。
     */
    private String resolveFileName(MultipartFile file, String objectKey) {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        if (StringUtils.hasText(originalFilename)) {
            String name = originalFilename.replace('\\', '/');
            int slashIndex = name.lastIndexOf('/');
            if (slashIndex >= 0) {
                name = name.substring(slashIndex + 1);
            }
            if (StringUtils.hasText(name)) {
                return truncate(name, MAX_FILE_NAME_LENGTH);
            }
        }
        return objectKey.substring(objectKey.lastIndexOf('/') + 1);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
