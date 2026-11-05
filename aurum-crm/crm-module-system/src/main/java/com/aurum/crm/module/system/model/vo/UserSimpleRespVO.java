package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 人员选择器专用（{@code GET /system/user/simple-list} 与 {@code /options}）。
 *
 * <p>被 3 处复用（接口文档第十节）：选直属上级、选部门负责人、选角色人员。
 *
 * <p><b>字段分两类用途，加字段时两类都要想到：</b>
 * <ul>
 *     <li><b>展示</b>：{@code name} / {@code username} / {@code phone} / {@code deptName} ——
 *         对应前端 {@code selectorRegistry} 的 {@code USER_COLUMNS}，
 *         少一个就那一列显示 `--`。</li>
 *     <li><b>筛选</b>：{@code deptId} —— 选择器弹窗左侧的「组织架构」树点某个部门时，
 *         要按<b>该部门及其全部子孙</b>过滤右侧人员，靠的就是这个字段做 id 匹配
 *         （见 {@code EntitySelectorDialog#displayList} 与 {@code collectDeptIds}）。</li>
 * </ul>
 *
 * <p>⚠️ <b>{@code deptId} 曾经漏掉</b>，而前端靠 {@code r.deptName === 节点名} 兜底 ——
 * 那只能精确匹配部门名、<b>不含子部门</b>：点「华科技集团」本该看到全公司 15 人，
 * 实际只看到 1 人（BUG-028）。加字段前请确认没有别的消费方只认旧字段集。
 *
 * <p>接口文档第十节还提醒「不要用 extra 拼字符串，直接返回结构化字段」，
 * 所以这里把要展示的字段都平铺出来，不用 {@code extra}。
 */
@Data
@Schema(description = "人员选择器项")
public class UserSimpleRespVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "手机号码")
    private String phone;

    @Schema(description = "所属部门 ID。⚠️ 给选择器的「组织架构」筛选用的，不只是展示字段")
    private Long deptId;

    @Schema(description = "所属部门名称")
    private String deptName;
}
