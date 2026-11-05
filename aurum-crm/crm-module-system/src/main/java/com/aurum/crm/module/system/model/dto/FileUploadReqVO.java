package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文件上传的业务绑定参数（{@code multipart/form-data} 里的普通字段，文件本体单独传）。
 *
 * <p><b>什么时候传、什么时候不传</b>：
 * <ul>
 *     <li>**传**（两个都传）：附件明确属于某个单据，例如合同扫描件 →
 *         {@code bizType=contract, bizId=合同ID}。此时会写 {@code sys_attachment}，
 *         详情页的「附件」Tab 才查得到</li>
 *     <li>**不传**：临时上传，例如「新增产品」表单里的产品图片 ——
 *         上传时产品还没保存、没有 productId。此时只返回 {@code url}，
 *         由前端存进 {@code crm_product.image}，<b>不写附件表</b>，不留无人引用的行</li>
 * </ul>
 */
@Data
@Schema(description = "文件上传参数")
public class FileUploadReqVO {

    @Schema(description = "业务类型（product / contract / customer / invoice ...），与 bizId 同时提供或不提供",
            example = "product")
    @Size(max = 32, message = "业务类型长度不能超过 32")
    private String bizType;

    @Schema(description = "业务ID（对应业务表主键）", example = "1001")
    private Long bizId;

    @Schema(description = "附件分类：1 合同扫描件 2 发票PDF 3 营业执照 4 其他，缺省 4", example = "4")
    private Integer attachmentType;
}
