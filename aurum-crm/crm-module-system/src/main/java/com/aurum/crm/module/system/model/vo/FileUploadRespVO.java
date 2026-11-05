package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件上传响应，对应《05-接口文档》3.7 —— 返回 {@code attachmentId} 与 {@code url}。
 *
 * <p>⚠️ {@code attachmentId} 在**临时上传**（未传 bizType/bizId）时为 null，
 * 而全局 Jackson 配了 {@code default-property-inclusion: non_null}，
 * 所以该字段在 JSON 里**直接不出现** —— 前端必须按「可缺省」处理，
 * 不能假设它一定存在。
 */
@Data
@Schema(description = "文件上传响应")
public class FileUploadRespVO {

    @Schema(description = "附件ID（未绑定业务单据时不返回）")
    private Long attachmentId;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "公网可访问 URL（bucket 公共读，前端可直接渲染/下载）")
    private String url;

    @Schema(description = "对象存储路径（排查问题与删除对象时使用）")
    private String objectKey;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "文件类型（MIME）")
    private String fileType;
}
