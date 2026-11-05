package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 产品图片上传响应。
 *
 * <p>前端拿到 {@code url} 后填进产品表单的 {@code image} 字段，
 * 再提交 {@code POST /crm/product} 或 {@code PUT /crm/product/{id}} 即可。
 *
 * <p><b>为什么只返回两个字段</b>：文件名、大小前端本来就已知；
 * 这里只需要「图片地址」和「对象路径」。{@code objectKey} 保留下来是为了
 * 排查问题，以及将来人工清理对象时不用从 URL 里反解。
 */
@Data
@Schema(description = "产品图片上传响应")
public class ProductImageRespVO {

    @Schema(description = "图片访问 URL（bucket 公共读，可直接渲染）",
            example = "https://qk-files.oss-cn-chengdu.aliyuncs.com/crm/product/2026/09/3f2a....png")
    private String url;

    @Schema(description = "对象存储路径（排查与人工清理用）",
            example = "crm/product/2026/09/3f2a....png")
    private String objectKey;
}
