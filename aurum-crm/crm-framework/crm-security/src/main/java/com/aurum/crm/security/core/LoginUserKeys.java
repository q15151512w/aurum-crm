package com.aurum.crm.security.core;

/**
 * Sa-Token Session 中存放登录信息的 key。
 *
 * <p>集中定义避免各处硬编码字符串拼错 —— 拼错不会报错，只会静默取到 null，
 * 排查起来很费时间。
 */
public final class LoginUserKeys {

    /** 登录账号 */
    public static final String USERNAME = "username";

    /** 真实姓名 */
    public static final String REAL_NAME = "realName";

    /** 所属部门 ID */
    public static final String DEPT_ID = "deptId";

    /** 数据范围：1 全部 / 2 自定义 / 3 本部门 / 4 本部门及以下 / 5 仅本人 */
    public static final String DATA_SCOPE = "dataScope";

    /** 自定义数据范围时可见的部门 ID 集合 */
    public static final String DATA_SCOPE_DEPT_IDS = "dataScopeDeptIds";

    /** 角色标识集合 */
    public static final String ROLE_KEYS = "roleKeys";

    /** 权限标识集合 */
    public static final String PERMISSIONS = "permissions";

    private LoginUserKeys() {
    }
}
