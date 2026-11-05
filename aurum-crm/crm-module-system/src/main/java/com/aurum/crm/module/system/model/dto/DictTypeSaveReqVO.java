package com.aurum.crm.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字典类型新增 / 修改请求（新增与编辑共用）。
 *
 * <p>字段名与前端表单模型一致：{@code name} / {@code type} / {@code status} / {@code remark}。
 *
 * <p><b>关于 {@code status} 为什么是中文</b>：本项目对**状态机枚举字段**的约定是
 * 「后端转中文标签返回、前端 options 保持中文」（见 BUG-002 确立的字段口径约定），
 * 与 {@code ContractStatus} / {@code OpportunityStage} 同一套做法。
 * 所以接口上走的是 {@code 正常} / {@code 停用}，落库前由 Service 转成 1 / 0。
 */
@Data
@Schema(description = "字典类型新增/修改")
public class DictTypeSaveReqVO {

    @Schema(description = "字典名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "客户来源")
    @NotBlank(message = "请输入字典名称")
    @Size(max = 100, message = "字典名称长度不能超过 100")
    private String name;

    /**
     * 字典类型编码。
     *
     * <p>这是程序里取字典用的 key（如 {@code crm_source}），建成后业务代码就在引用它了 ——
     * 前端编辑时置灰不给改，服务端也不在 update 时更新它。
     */
    @Schema(description = "字典类型编码（程序取值用的 key，编辑时不修改）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "crm_source")
    @NotBlank(message = "请输入字典类型编码")
    @Size(max = 100, message = "字典类型编码长度不能超过 100")
    private String type;

    @Schema(description = "状态：正常 / 停用；不传 = 正常", example = "正常")
    private String status;

    @Schema(description = "备注", example = "客户/线索来源")
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
