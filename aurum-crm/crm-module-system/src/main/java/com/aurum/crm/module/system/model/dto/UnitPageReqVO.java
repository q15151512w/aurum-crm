package com.aurum.crm.module.system.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 计量单位分页查询（《05-接口文档》3.6 / 《14-计量单位模块接口文档》1.3）。
 *
 * <p>⚠️ 前端列表页（{@code ProList}）会恒定带上一个 {@code scope=all} —— 那是 16 个列表页共用的
 * 组件行为，本页没有筛选胶囊、用不到它。这里**故意不声明** {@code scope} 字段：
 * Spring 的 bean 绑定会静默忽略未声明的查询参数，正好就是我们要的「未知值不筛选」。
 * （不要为了「显式忽略」去声明它 —— 声明了反而要维护一个永远不生效的分支。）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "计量单位分页查询")
public class UnitPageReqVO extends PageParam {

    @Schema(description = "关键字，同时匹配单位名称与单位编码（空串等于不筛）", example = "台")
    private String keyword;

    @Schema(description = "状态：0 停用 / 1 启用；不传 = 全部", example = "1")
    private Integer status;
}
