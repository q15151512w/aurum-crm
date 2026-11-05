package com.aurum.crm.module.system.model;

import java.util.Arrays;

/**
 * 操作日志的业务类型，对应 {@code sys_operation_log.business_type}。
 *
 * <p><b>为什么转中文</b>：按《05-接口文档》1.8 的字段口径约定，取值写死在 Java 枚举里的
 * 属于「状态机枚举字段」，<b>由后端转成中文标签下发</b>，前端不做映射
 * （与 {@code ContractStatus} / {@code OpportunityStage} 同一套做法）。
 * 前端 {@code operation.vue} 的「操作」列直接显示这个标签。
 *
 * <p>取值与 DDL 注释一致：0 其他 / 1 新增 / 2 修改 / 3 删除 / 4 查询 / 5 导出 / 6 导入。
 */
public enum OperationBusinessType {

    /** 其他（未能从 HTTP 动词推断出更具体的语义时用这个） */
    OTHER(0, "其他"),

    /** 新增 */
    CREATE(1, "新增"),

    /** 修改 */
    UPDATE(2, "修改"),

    /** 删除 */
    DELETE(3, "删除"),

    /** 查询 */
    QUERY(4, "查询"),

    /** 导出 */
    EXPORT(5, "导出"),

    /** 导入 */
    IMPORT(6, "导入");

    private final int code;
    private final String label;

    OperationBusinessType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 码值 → 枚举，未知码值返回 {@link #OTHER} 而不是 {@code null}。
     *
     * <p>日志是历史数据，将来枚举增删都可能让老记录出现「当前代码里没有的码值」，
     * 那时宁可显示「其他」也不该让整个列表 500。
     */
    public static OperationBusinessType ofCode(Integer code) {
        if (code == null) {
            return OTHER;
        }
        return Arrays.stream(values())
                .filter(t -> t.code == code)
                .findFirst()
                .orElse(OTHER);
    }

    /** 码值 → 中文标签。日志列表展示用 */
    public static String labelOf(Integer code) {
        return ofCode(code).getLabel();
    }
}
