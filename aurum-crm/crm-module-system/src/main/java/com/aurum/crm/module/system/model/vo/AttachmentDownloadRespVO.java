package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.core.io.Resource;

/**
 * 附件下载的载荷（Service → Controller 之间传递，不直接序列化给前端）。
 *
 * <p>{@code resource} 是 Spring 核心抽象 {@link Resource}（不是 Web 类型），
 * 由 Controller 组装成 {@code ResponseEntity<Resource>} 流式写出 ——
 * 这样 Service 层不依赖 Web API，测试里也能直接把内容读出来比对。
 */
@Data
@Schema(description = "附件下载载荷")
public class AttachmentDownloadRespVO {

    @Schema(description = "原始文件名（用于 Content-Disposition）")
    private String fileName;

    @Schema(description = "文件大小（字节，用于 Content-Length）")
    private Long fileSize;

    @Schema(description = "MIME 类型，为空时回退 application/octet-stream")
    private String contentType;

    @Schema(description = "文件内容")
    private Resource resource;
}
