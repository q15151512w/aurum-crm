package com.aurum.crm.common.core;

import lombok.Data;

import java.io.Serializable;
import java.util.Set;

/**
 * 登录用户上下文。
 *
 * <p>放在 crm-common 而不是 crm-security，是为了让 crm-mybatis 也能读到它
 * （字段自动填充需要当前用户 ID），同时避免 {@code mybatis -> security} 的循环依赖。
 *
 * <p><b>类加载顺序</b>：security 写 → mybatis 读，两边只依赖 common，依赖图是单向的。
 */
@Data
public class LoginUser implements Serializable {

    /** 用户 ID */
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 所属部门 ID */
    private Long deptId;

    /**
     * 该用户所有角色中「最宽」的数据范围。
     *
     * <p>取值见建表规范：1 全部 / 2 自定义 / 3 本部门 / 4 本部门及以下 / 5 仅本人。
     * <p>登录时由后端算好写入，<b>不要</b>让前端传、也不要在每次查询时重算。
     */
    private Integer dataScope;

    /** 自定义数据范围时，可见的部门 ID 集合 */
    private Set<Long> dataScopeDeptIds;

    /** 角色标识集合，如 {@code ["sales", "sales_director"]} */
    private Set<String> roleKeys;

    /** 权限标识集合，如 {@code ["crm:customer:list", "crm:customer:create"]} */
    private Set<String> permissions;
}
