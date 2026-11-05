package com.aurum.crm.module.system.service;

import com.aurum.crm.module.system.model.dto.FileUploadReqVO;
import com.aurum.crm.module.system.model.vo.AttachmentDownloadRespVO;
import com.aurum.crm.module.system.model.vo.FileUploadRespVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务：编排「上传 OSS + 落 {@code sys_attachment}」，以及附件的下载与删除。
 *
 * <p>路由见《05-接口文档》3.7 附件：
 * {@code POST /system/file/upload}、{@code GET /system/file/{id}}、
 * {@code DELETE /system/file/{id}}。
 *
 * <p>实现类：{@link com.aurum.crm.module.system.service.impl.FileServiceImpl}。
 */
public interface FileService {

    /**
     * 上传文件。
     *
     * @param file  文件本体
     * @param reqVO 业务绑定参数；{@code bizType} 与 {@code bizId} 同时提供才会写附件表
     * @return 上传结果（含公网 URL）
     */
    FileUploadRespVO upload(MultipartFile file, FileUploadReqVO reqVO);

    /**
     * 下载附件。
     *
     * <p>只认 {@code sys_attachment} 里存在的记录（已逻辑删除的取不到），
     * 再去 OSS 取对象内容流式返回 —— 不走 302 跳转到 OSS URL，
     * 因为那样会受 OSS 侧 CORS 限制，且中文文件名无法控制。
     *
     * @param id 附件ID
     * @throws com.aurum.crm.common.exception.ServiceException 附件不存在，或 OSS 上对象已丢失
     */
    AttachmentDownloadRespVO download(Long id);

    /**
     * 删除附件。
     *
     * <p><b>先逻辑删除数据库记录，再尽力删除 OSS 对象</b>。
     * 顺序是刻意的：库里删了而对象没删掉，只是留下孤儿对象（用户无感）；
     * 反过来先删对象而库删除失败，附件行还在但点下载就报错（用户可见）。
     *
     * @param id 附件ID
     * @throws com.aurum.crm.common.exception.ServiceException 附件不存在
     */
    void delete(Long id);
}
