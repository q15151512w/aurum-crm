package com.aurum.crm.module.system.model;

/**
 * 操作日志的执行结果，对应 {@code sys_operation_log.status}（0 失败 / 1 成功）。
 *
 * <p>⚠️ <b>这是「操作结果」，不要与 {@code sys_user.status}（1 正常 / 2 禁用 / 3 离职）
 * 或 {@code sys_login_log.status}（0 失败 / 1 成功）混用</b> —— 后两者语义完全不同。
 *
 * <p><b>为什么要转中文</b>：前端 {@code operation.vue} 的 {@code #cell-status} 插槽是靠
 * {@code value === '成功'} 来判断标签颜色的：
 * <pre>{@code
 * <el-tag :type="value === '成功' ? 'success' : 'danger'">
 * }</pre>
 * 所以接口<b>必须下发中文</b>。下发数字 {@code 1} 的话，每一行都会走到 else 分支，
 * 整列渲染成表示失败的红色 —— 界面上看起来「所有操作都失败了"，
 * 而后端日志里其实全是成功，会把人往错的方向带很久。
 */
public final class OperationResult {

    /** 失败 */
    public static final Integer FAILURE = 0;

    /** 成功 */
    public static final Integer SUCCESS = 1;

    private OperationResult() {
    }

    /**
     * 码值 → 中文标签。
     *
     * <p>只有「成功」是成功，其余一律按失败展示：脏数据（NULL / 3 / -1）显示成失败，
     * 比显示成成功安全 —— 审计场景下宁可误报也不漏报。
     */
    public static String labelOf(Integer status) {
        return SUCCESS.equals(status) ? "成功" : "失败";
    }
}
