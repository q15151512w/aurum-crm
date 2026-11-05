package com.aurum.crm.module.system.core;

import com.aurum.crm.common.core.DataScope;
import com.aurum.crm.common.core.SuperPermission;

import java.time.Duration;
import java.util.Set;

/**
 * 认证模块常量。
 *
 * <p>集中定义，避免 Redis key / 状态码 / 提示文案散落在各处 ——
 * 拼错字符串不会报错，只会静默取到 null 或走错分支，排查很费时间。
 */
public final class AuthConstants {

    // ------------------------------------------------------------------
    //  Redis key（前缀 auth: 是本项目自定义命名空间，与 Sa-Token 的 satoken: 不冲突）
    // ------------------------------------------------------------------

    /**
     * 邮箱验证码本体：{@code auth:email:code:{email}}。
     *
     * <p>⚠️ 邮箱在这里一律<b>小写归一化</b>后再拼 key（见 {@code EmailCodeServiceImpl#normalize}），
     * 否则 {@code A@x.com} 与 {@code a@x.com} 会落成两个 key ——
     * 用户用不同大小写重发就能绕过 60 秒限流。
     */
    public static final String EMAIL_CODE_KEY = "auth:email:code:";

    /** 发送限流：{@code auth:email:limit:{email}}，同样归一化后拼 */
    public static final String EMAIL_LIMIT_KEY = "auth:email:limit:";

    /**
     * 验证码连续校验失败计数：{@code auth:email:fail:{email}}。
     *
     * <p><b>为什么需要它</b>：验证码是 6 位数字（100 万种），而校验成功前不会作废它 ——
     * 没有次数上限的话，攻击者拿到一个验证码的有效期（5 分钟）内可以无限猜，
     * 而且 60 秒就能重发一次、把窗口无限续期。这是唯一挡住「邮箱验证码登录被爆破」的闸门。
     *
     * <p>达到 {@link #EMAIL_CODE_MAX_ATTEMPTS} 后<b>验证码本身会被删掉</b>，
     * 必须重新获取才能再试；重发成功时本计数也会清零（否则改错 5 次的用户重发后仍然是被锁死状态）。
     */
    public static final String EMAIL_CODE_FAIL_KEY = "auth:email:fail:";

    /**
     * 同一个验证码最多允许连续校验失败几次。
     *
     * <p>取 5 是在「挡爆破」和「别烦正常人」之间取的折中：正常用户输错一两次就该成功了，
     * 真要连错 5 次，让他重新收一封信并不算过分。
     */
    public static final int EMAIL_CODE_MAX_ATTEMPTS = 5;

    /** 验证码有效期 5 分钟 */
    public static final Duration EMAIL_CODE_TTL = Duration.ofMinutes(5);

    /** 同一邮箱 60 秒内只能发一次 */
    public static final Duration EMAIL_LIMIT_TTL = Duration.ofSeconds(60);

    // ------------------------------------------------------------------
    //  算术验证码（登录防爆破，见接口文档 2.9）
    // ------------------------------------------------------------------

    /** 验证码答案：{@code auth:captcha:{captchaKey}} */
    public static final String CAPTCHA_KEY = "auth:captcha:";

    /** 验证码有效期 */
    public static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);

    /**
     * 运算数的取值范围（含两端）：<b>0 ~ 9</b>。
     *
     * <p>⚠️ 含 0 会不时出现「6 × 0 = ?」「0 + 3 = ?」这类一眼出答案的送分题 ——
     * 这是刻意接受的：题目本身明文下发，防护强度主要靠「一次性消费 + 挡在 BCrypt 之前」，
     * 不指望题目难度。若要收紧，把 {@code CAPTCHA_MIN} 改成 1 即可。
     *
     * <p>⚠️ 上界是 9 而不是 10 —— 要求「不超过 10」时取的是严格小于 10。
     * {@link #CAPTCHA_MAX} 本身是<b>闭区间端点</b>。
     */
    public static final int CAPTCHA_MIN = 0;
    public static final int CAPTCHA_MAX = 9;

    // ------------------------------------------------------------------
    //  角色与权限
    // ------------------------------------------------------------------

    /** 超级管理员角色标识 */
    public static final String SUPER_ROLE_KEY = "admin";

    // ------------------------------------------------------------------
    //  登录日志
    // ------------------------------------------------------------------

    /**
     * Sa-Token Session 里存「本次登录对应的 {@code sys_login_log.log_id}」的 key。
     *
     * <p><b>为什么需要它</b>：登出时要回填 {@code logout_time}，而
     * {@code sys_login_log} 与登录会话之间没有任何天然关联
     * （按 {@code user_id} 反查最近一条会在多端登录时串号）。
     * 登录成功时把新生成的 id 记进 Session，是最精确的关联方式。
     *
     * <p>⚠️ 必须在 {@code StpUtil.logout()} <b>之前</b>读出来 —— 注销后 Session 就没了。
     */
    public static final String LOGIN_LOG_ID_SESSION_KEY = "loginLogId";

    /** 登录方式：密码 */
    public static final Integer LOGIN_TYPE_PASSWORD = 1;

    /**
     * 登录方式：邮箱验证码。
     *
     * <p>码值 <b>2</b> 是原「短信验证码登录」沿用的值 ——
     * 换成邮箱后语义变了但码值不变，这样历史登录日志（{@code sys_login_log.login_type=2}）
     * 仍然是一个一致的类别（「验证码登录」），不用写数据迁移。
     */
    public static final Integer LOGIN_TYPE_EMAIL = 2;

    /**
     * 超管的通配权限。
     *
     * <p>⚠️ 这个值<b>不在数据库里</b>（{@code sys_menu} 种子里没有这一行），
     * 必须由后端为 admin 角色合成。
     * 忘了合成的话，前端的超管判断会失败、按钮全部置灰，
     * 而登录本身是成功的 —— 这种「半坏」最难定位。
     *
     * <p>字面量已上提到 {@link SuperPermission#WILDCARD}（crm-common），
     * 因为 {@code crm-security} 做权限匹配时也要用同一个值、却依赖不到本模块。
     * <b>别在这里另写字符串</b>，两份定义迟早漂移 —— 与 {@link DataScope} 同一个理由。
     *
     * <p>⚠️ 光有合成还不够：{@code *:*:*} 默认<b>匹配不了两段式</b>权限码
     * （{@code dashboard:view}），需要 {@code SaTokenConfig#overridePermissionMatching()}
     * 显式把它的语义钉成「全部权限」。详见 BUG-003。
     */
    public static final Set<String> SUPER_PERMISSIONS = Set.of(SuperPermission.WILDCARD);

    // ------------------------------------------------------------------
    //  用户状态（sys_user.status：1 正常 / 2 禁用 / 3 离职）
    // ------------------------------------------------------------------

    public static final Integer USER_STATUS_NORMAL = 1;
    public static final Integer USER_STATUS_DISABLED = 2;
    public static final Integer USER_STATUS_RESIGNED = 3;

    // ------------------------------------------------------------------
    //  其他表的状态（注意这几张表的 status 语义各不相同，别混用）
    // ------------------------------------------------------------------

    /** 角色状态（sys_role.status：0 停用 / 1 正常） */
    public static final Integer ROLE_STATUS_NORMAL = 1;

    /** 部门状态（sys_dept.status：0 停用 / 1 正常） */
    public static final Integer DEPT_STATUS_NORMAL = 1;

    /** 菜单状态（sys_menu.status：0 停用 / 1 正常） */
    public static final Integer MENU_STATUS_NORMAL = 1;

    /** 菜单树根节点的 parent_id */
    public static final Long MENU_ROOT_PARENT_ID = 0L;

    /**
     * 向上补全祖先链的循环上限。
     *
     * <p>正常数据只有 3 层，这个上限纯粹是防御脏数据（如 A→B→A 的环）把
     * {@code while} 变成死循环。
     */
    public static final int MAX_MENU_DEPTH = 16;

    // 数据范围取值（sys_role.data_scope）不在这里定义 ——
    // ⚠️ 已上提到 com.aurum.crm.common.core.DataScope：
    //    数据权限拦截器在 crm-mybatis 里，它只依赖 crm-common、依赖不到本模块，
    //    常量留在这一份就会出现两套定义、慢慢漂移。请用 DataScope.ALL / CUSTOM / ...

    // ------------------------------------------------------------------
    //  1007「账号未启用」的分支文案（文档 2.1）
    // ------------------------------------------------------------------

    /** status=2 且 sys_user_role 里没有任何行 */
    public static final String MSG_ACCOUNT_PENDING_ROLE = "账号待管理员分配角色后启用";

    /** status=2 且已分配过角色 */
    public static final String MSG_ACCOUNT_DISABLED = "账号已被禁用，请联系管理员";

    /** status=3 */
    public static final String MSG_ACCOUNT_RESIGNED = "该账号已离职";

    private AuthConstants() {
    }
}
