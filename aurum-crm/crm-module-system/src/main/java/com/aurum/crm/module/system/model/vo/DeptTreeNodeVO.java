package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门树节点（{@code GET /system/dept/tree}）。
 *
 * <p>⚠️ <b>字段名是 {@code id} / {@code name}，不是 {@code deptId} / {@code deptName}</b> ——
 * 前端 {@code DeptTreeSelect.vue} 用的是 {@code node-key="id"} +
 * {@code :props="{ label: 'name', children: 'children' }"}`，
 * 字段名对不上就是一棵空树（而且是静默的，不报错）。
 *
 * <p>这与「接口文档第十节 · 选择器统一返回 {@code {id, name, extra}}」的约定一致。
 */
@Data
@Schema(description = "部门树节点")
public class DeptTreeNodeVO {

    @Schema(description = "部门ID")
    private Long id;

    @Schema(description = "部门名称")
    private String name;

    /**
     * 部门类型，**英文字符串** {@code group} / {@code dept} / {@code team}。
     *
     * <p>前端组织架构页拿它当 CSS 类名（{@code dept-badge--${type}}）并查
     * {@code TYPE_LABEL[type]} 显示徽标文字，所以这里必须回传**字符串**而不是码值。
     * 库里存的是 TINYINT（1/2/3），转换在 Service 层做。
     */
    @Schema(description = "类型：group 集团 / dept 部门 / team 组")
    private String type;

    /**
     * 部门负责人姓名，未设置时返回空串。
     *
     * <p>⚠️ 给空串而不是 null：前端模板里直接 {@code {{ data.leaderName || '--' }}}，
     * 传 null 会渲染成 "null" 而不是走兜底。
     */
    @Schema(description = "部门负责人姓名（未设置时为空串）")
    private String leaderName;

    /** ⚠️ 必须初始化为空 List：前端靠它判断有没有子节点，null 会出错 */
    @Schema(description = "子部门")
    private List<DeptTreeNodeVO> children = new ArrayList<>();
}
