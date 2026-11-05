package com.aurum.crm.module.system.core;

import com.aurum.crm.common.core.DataScope;
import com.aurum.crm.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;

/**
 * 「中文展示口径」 ↔ 「数据库 TINYINT 码值」的双向转换。
 *
 * <h3>为什么需要这一层</h3>
 *
 * <p>前端 {@code aurum-crm-ui} 的页面与表单**全程用中文传值**：
 * 性别发「男/女」、在职状态发「在职/离职」、账号状态发「已激活/已禁用」、
 * 角色数据范围发「全部数据/本部门及以下/…」。而库里的列都是 TINYINT 码值。
 *
 * <p>这是《05-接口文档》1.2 与联系人模块注释里已经承认过的现象 ——
 * **前端用展示口径，DDL 是权威**。转换放在后端做，前端零改动。
 *
 * <h3>⚠️ 未知取值一律抛错，不静默兜底</h3>
 *
 * <p>把「未知性别」悄悄当成「男」、「未知状态」悄悄当成「正常」，
 * 会<b>写错数据而前端完全看不出来</b> —— 这类 bug 最难查。
 * 所以这里查不到就抛 400，让问题当场暴露。
 *
 * <p>唯一例外见 {@link #inferStatus}：两个字段都为空时按「正常」处理
 * （编辑时不改状态的正常情形），但那是有意设计而非兜底未知值。
 */
@Slf4j
public final class DisplayEnumTranslator {

    // ========================================================================
    //  性别：sys_user.gender —— 0 未知 / 1 男 / 2 女
    // ========================================================================

    private static final String GENDER_MALE = "男";
    private static final String GENDER_FEMALE = "女";

    public static Integer genderToCode(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return switch (text.trim()) {
            case GENDER_MALE -> 1;
            case GENDER_FEMALE -> 2;
            case "未知" -> 0;
            default -> throw unknown("性别", text, "男/女");
        };
    }

    public static String genderToText(Integer code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case 1 -> GENDER_MALE;
            case 2 -> GENDER_FEMALE;
            default -> "未知";
        };
    }

    // ========================================================================
    //  账号状态：sys_user.status —— 1 正常 / 2 禁用 / 3 离职
    //
    //  ⚠️ 前端是【两个】字段（workStatus 在职/离职、accountStatus 已激活/已禁用），
    //     库里只有【一个】列。入参要合成、出参要拆分。
    // ========================================================================

    private static final String WORK_ON = "在职";
    private static final String WORK_OFF = "离职";
    private static final String ACCOUNT_ACTIVE = "已激活";
    private static final String ACCOUNT_DISABLED = "已禁用";

    /**
     * 把前端的两个展示字段合成一个 status 码值。
     *
     * <p><b>优先级是刻意固定的</b>（照抄前端 mock 的既有口径）：
     * <pre>
     *   workStatus == "离职"          → 3 离职
     *   否则 accountStatus == "已禁用" → 2 禁用
     *   否则                          → 1 正常
     * </pre>
     * 「离职」优先于「已禁用」：三态互斥，同时选中时以更严重的为准。
     * 不固定优先级的话，用户同时勾了这两项，结果就不确定了。
     */
    public static Integer inferStatus(String workStatus, String accountStatus) {
        if (WORK_OFF.equals(trim(workStatus))) {
            return AuthConstants.USER_STATUS_RESIGNED;
        }
        if (ACCOUNT_DISABLED.equals(trim(accountStatus))) {
            return AuthConstants.USER_STATUS_DISABLED;
        }
        // 两个都为空（如编辑时不改状态）按「正常」；填了但值不认识则报错
        checkKnown(workStatus, WORK_ON, WORK_OFF, "在职状态");
        checkKnown(accountStatus, ACCOUNT_ACTIVE, ACCOUNT_DISABLED, "账号状态");
        return AuthConstants.USER_STATUS_NORMAL;
    }

    public static String workStatusToText(Integer status) {
        // 只有「离职」会显示成离职；禁用时人还在职，只是账号被禁
        return AuthConstants.USER_STATUS_RESIGNED.equals(status) ? WORK_OFF : WORK_ON;
    }

    public static String accountStatusToText(Integer status) {
        return AuthConstants.USER_STATUS_DISABLED.equals(status) ? ACCOUNT_DISABLED : ACCOUNT_ACTIVE;
    }

    // ========================================================================
    //  数据范围：sys_role.data_scope —— 1 全部 / 2 自定义 / 3 本部门 / 4 本部门及以下 / 5 仅本人
    // ========================================================================

    private static final String SCOPE_ALL = "全部数据";
    private static final String SCOPE_CUSTOM = "自定义";
    private static final String SCOPE_DEPT = "本部门";
    private static final String SCOPE_DEPT_AND_CHILD = "本部门及以下";
    private static final String SCOPE_SELF = "仅本人";

    public static Integer dataScopeToCode(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return switch (text.trim()) {
            case SCOPE_ALL -> DataScope.ALL;
            case SCOPE_CUSTOM -> DataScope.CUSTOM;
            case SCOPE_DEPT -> DataScope.DEPT;
            case SCOPE_DEPT_AND_CHILD -> DataScope.DEPT_AND_CHILD;
            case SCOPE_SELF -> DataScope.SELF;
            default -> throw unknown("数据范围", text,
                    "全部数据/自定义/本部门/本部门及以下/仅本人");
        };
    }

    public static String dataScopeToText(Integer code) {
        if (code == null) {
            return null;
        }
        if (DataScope.ALL.equals(code)) {
            return SCOPE_ALL;
        }
        if (DataScope.CUSTOM.equals(code)) {
            return SCOPE_CUSTOM;
        }
        if (DataScope.DEPT.equals(code)) {
            return SCOPE_DEPT;
        }
        if (DataScope.DEPT_AND_CHILD.equals(code)) {
            return SCOPE_DEPT_AND_CHILD;
        }
        if (DataScope.SELF.equals(code)) {
            return SCOPE_SELF;
        }
        // 码值来自库里，出现不认识的值说明数据被脏写 —— 记日志但不抛，
        // 免得一条脏数据让整个列表接口 500。展示成 null 由前端做兜底
        log.warn("[展示口径] 未知的 data_scope 码值: {}", code);
        return null;
    }

    // ========================================================================
    //  部门类型：sys_dept.type —— 1 集团 / 2 部门 / 3 组
    //
    //  ⚠️ 与上面几组不同：前端这里传的是【英文字符串】group/dept/team
    //     （见 aurum-crm-ui/src/constants/options.js 的 DEPT_TYPE_OPTIONS），
    //     不是中文。别按前几组的习惯去找中文映射。
    // ========================================================================

    private static final String DEPT_GROUP = "group";
    private static final String DEPT_DEPT = "dept";
    private static final String DEPT_TEAM = "team";

    public static Integer deptTypeToCode(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return switch (text.trim()) {
            case DEPT_GROUP -> 1;
            case DEPT_DEPT -> 2;
            case DEPT_TEAM -> 3;
            default -> throw unknown("部门类型", text, "group/dept/team");
        };
    }

    public static String deptTypeToText(Integer code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case 1 -> DEPT_GROUP;
            case 3 -> DEPT_TEAM;
            // 2 与未知值都按「部门」处理：树上的徽标底色错一个不影响功能
            default -> DEPT_DEPT;
        };
    }

    // ========================================================================

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    /** 填了值但不在允许集合里 → 报错（空值放行，交给调用方决定默认值） */
    private static void checkKnown(String value, String a, String b, String label) {
        if (value == null || value.isBlank()) {
            return;
        }
        String v = value.trim();
        if (!v.equals(a) && !v.equals(b)) {
            throw unknown(label, value, a + "/" + b);
        }
    }

    private static ServiceException unknown(String label, String value, String allowed) {
        log.warn("[展示口径] {} 收到未知取值: {}（允许：{}）", label, value, allowed);
        return new ServiceException(400, label + "取值不正确：" + value + "，允许 " + allowed);
    }

    private DisplayEnumTranslator() {
    }
}
