package com.aurum.crm.security.core;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Sa-Token 权限数据源：把登录时写进 Session 的角色/权限交给 Sa-Token。
 *
 * <p><b>为什么必须有这个类</b>：Sa-Token 的 {@code @SaCheckPermission} / {@code @SaCheckRole}
 * 校验时会回调 {@link StpInterface}。不提供实现时它默认返回<b>空集合</b>，
 * 结果是「所有权限注解都拒绝所有人」—— 登录明明是成功的，但每个带注解的接口都 403，
 * 排查方向很容易跑偏到角色配置上。
 *
 * <p>数据来源是登录接口算好写进 Session 的那两份集合（见 {@code AuthServiceImpl.writeSession}），
 * 因此这里<b>不查库</b>，避免每次鉴权都打一次数据库。
 *
 * <p>超管的 {@code *:*:*} 也在 Session 里。⚠️ 但要注意它<b>不是「匹配一切」的内建语法</b>：
 * 它只是 Sa-Token 的一个普通通配模式（{@code SaFoxUtil.vagueMatch}），而模式里的 {@code :}
 * 是字面量，所以默认可匹配的只有<b>三段式</b>标识 ——
 * {@code crm:lead:list} 能命中，两段式的 {@code dashboard:view} <b>命中不了</b>。
 *
 * <p>这条差异曾导致「超管唯独进不去首页看板」（BUG-003）。
 * 「{@code *:*:*} 等价于全部权限」这层语义由
 * {@link com.aurum.crm.security.config.SaTokenConfig#overridePermissionMatching()} 显式提供，
 * 不要把本类当成它会自动生效。
 *
 * @see com.aurum.crm.security.config.SaTokenConfig
 */
@Component
public class SaPermissionImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return readList(loginId, LoginUserKeys.PERMISSIONS);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return readList(loginId, LoginUserKeys.ROLE_KEYS);
    }

    /**
     * 从 Session 读取一个字符串集合。
     *
     * @param isCreate 传 {@code false}：会话不存在时不要顺手创建 ——
     *                 鉴权是只读操作，凭空建会话会留下永不过期的空 Session
     */
    private List<String> readList(Object loginId, String key) {
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        if (session == null) {
            return List.of();
        }
        Object v = session.get(key);
        if (!(v instanceof Collection<?> c)) {
            return List.of();
        }
        // 逐元素 toString()：经 Redis(Jackson) 反序列化后元素类型不确定，强转会静默失效
        List<String> result = new ArrayList<>(c.size());
        for (Object e : c) {
            if (e != null) {
                result.add(e.toString());
            }
        }
        return result;
    }
}
