package com.aurum.crm.module.system.service.impl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.aurum.crm.common.core.DataScope;
import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.system.core.AuthConstants;
import com.aurum.crm.module.system.core.DisplayEnumTranslator;
import com.aurum.crm.module.system.core.UserAgentParser;
import com.aurum.crm.module.system.dal.dataobject.SysDeptDO;
import com.aurum.crm.module.system.dal.dataobject.SysMenuDO;
import com.aurum.crm.module.system.dal.dataobject.SysRoleDO;
import com.aurum.crm.module.system.dal.dataobject.SysRoleDeptDO;
import com.aurum.crm.module.system.dal.dataobject.SysRoleMenuDO;
import com.aurum.crm.module.system.dal.dataobject.SysUserDO;
import com.aurum.crm.module.system.dal.dataobject.SysUserRoleDO;
import com.aurum.crm.module.system.dal.mysql.SysDeptMapper;
import com.aurum.crm.module.system.dal.mysql.SysMenuMapper;
import com.aurum.crm.module.system.dal.mysql.SysRoleDeptMapper;
import com.aurum.crm.module.system.dal.mysql.SysRoleMapper;
import com.aurum.crm.module.system.dal.mysql.SysRoleMenuMapper;
import com.aurum.crm.module.system.dal.mysql.SysUserMapper;
import com.aurum.crm.module.system.dal.mysql.SysUserRoleMapper;
import com.aurum.crm.module.system.model.convert.AuthConvert;
import com.aurum.crm.module.system.model.dto.ChangePasswordReqVO;
import com.aurum.crm.module.system.model.dto.ForgotPasswordReqVO;
import com.aurum.crm.module.system.model.dto.LoginReqVO;
import com.aurum.crm.module.system.model.dto.ProfileUpdateReqVO;
import com.aurum.crm.module.system.model.dto.RegisterReqVO;
import com.aurum.crm.module.system.model.dto.EmailCodeReqVO;
import com.aurum.crm.module.system.model.dto.EmailLoginReqVO;
import com.aurum.crm.module.system.model.vo.CaptchaRespVO;
import com.aurum.crm.module.system.model.vo.LoginRespVO;
import com.aurum.crm.module.system.model.vo.MenuRespVO;
import com.aurum.crm.module.system.model.vo.UserInfoRespVO;
import com.aurum.crm.module.system.service.AuthService;
import com.aurum.crm.module.system.service.CaptchaService;
import com.aurum.crm.module.system.service.LoginLogService;
import com.aurum.crm.module.system.service.EmailCodeService;
import com.aurum.crm.security.core.LoginUserKeys;
import com.aurum.crm.security.core.PasswordEncoder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证服务实现。
 *
 * <p><b>设计要点</b>：密码登录与邮箱验证码登录只在「怎么证明你是你」这一步不同，
 * 从「算登录上下文」往后完全共用 {@link #doLogin} —— 两条入口各写一套的话，
 * 将来加字段必然漏改一处。
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysRoleMapper sysRoleMapper;
    @Resource
    private SysMenuMapper sysMenuMapper;
    @Resource
    private SysDeptMapper sysDeptMapper;
    @Resource
    private SysUserRoleMapper sysUserRoleMapper;
    @Resource
    private SysRoleMenuMapper sysRoleMenuMapper;
    @Resource
    private SysRoleDeptMapper sysRoleDeptMapper;
    @Resource
    private EmailCodeService emailCodeService;
    @Resource
    private CaptchaService captchaService;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private AuthConvert authConvert;
    @Resource
    private LoginLogService loginLogService;

    // ========================================================================
    //  登录
    // ========================================================================

    @Override
    public void sendEmailCode(EmailCodeReqVO reqVO) {
        // 不做「邮箱是否注册过」的判断 —— 文档 2.2 明确要求不泄露邮箱是否注册过。
        // 注册本身就要求邮箱未注册，但那个判断放在 register 里，不能在发码时就暴露出来。
        emailCodeService.sendCode(reqVO.getEmail());
    }

    @Override
    public CaptchaRespVO captcha() {
        return captchaService.generate();
    }

    @Override
    public LoginRespVO login(LoginReqVO reqVO, String clientIp, String userAgent) {
        String[] ua = UserAgentParser.parse(userAgent);
        try {
            // ① 先验算术验证码。
            //
            // ⚠️ 位置很关键：必须在 passwordEncoder.matches 之前。
            //    BCrypt strength=10 单次 matches 要烧 50~100ms CPU（这是故意设计得慢的），
            //    若把验证码放在密码之后校验，攻击者每次请求都能逼服务器算一次 BCrypt，
            //    登录接口就成了 CPU 放大器。先验验证码（一次 Redis 往返，微秒级）
            //    能把盲目重放的请求挡在昂贵的哈希计算之前。
            captchaService.verifyAndConsume(reqVO.getCaptchaKey(), reqVO.getCaptchaCode());

            // ② 再查用户 / 验密码
            SysUserDO user = sysUserMapper.selectOne(SysUserDO::getUsername, reqVO.getUsername());

            // 用户不存在与密码错误返回同一个错误码 —— 否则接口会变成「账号枚举器」，
            // 攻击者能靠错误码差异把系统里有哪些账号问出来
            if (user == null) {
                throw new ServiceException(ErrorCode.USER_PASSWORD_ERROR);
            }
            if (!passwordEncoder.matches(reqVO.getPassword(), user.getPassword())) {
                throw new ServiceException(ErrorCode.USER_PASSWORD_ERROR);
            }

            // ⚠️ 顺序是「先验密码、再判状态」。
            //    反过来的话，未持有密码的人也能通过错误码差异探测出
            //    「这个账号存在但被禁用了」。
            return doLogin(user, clientIp, ua, AuthConstants.LOGIN_TYPE_PASSWORD);
        } catch (ServiceException e) {
            // 失败也留痕：哪个账号、从哪个 IP、什么时候试的 —— 这是防爆破审计的基础数据。
            // ⚠️ userId 只能给 null：账号可能压根不存在。
            loginLogService.recordLoginFailure(reqVO.getUsername(), null, clientIp,
                    ua[0], ua[1], AuthConstants.LOGIN_TYPE_PASSWORD, failureMsg(e));
            throw e;
        }
    }

    @Override
    public LoginRespVO emailLogin(EmailLoginReqVO reqVO, String clientIp, String userAgent) {
        String[] ua = UserAgentParser.parse(userAgent);
        try {
            // 验证码就是这里的身份凭证，验通过后立即失效
            emailCodeService.verifyAndConsume(reqVO.getEmail(), reqVO.getEmailCode());

            List<SysUserDO> users = sysUserMapper.selectList(SysUserDO::getEmail, reqVO.getEmail());
            if (users.isEmpty()) {
                throw new ServiceException(ErrorCode.USER_NOT_EXISTS, "该邮箱未注册");
            }
            if (users.size() > 1) {
                // sys_user 上有 uk_email (email, is_active) 唯一索引，正常不该出现多个。
                // 这里保留防御分支：万一索引被误删、或将来有人绕过索引写入（如直接改库），
                // 也不能随便挑一个账号登进去 —— 那等于「登录到了别人的账号」。
                log.warn("[邮箱登录] 邮箱命中多个账号: email={} count={}", reqVO.getEmail(), users.size());
                throw new ServiceException(ErrorCode.USER_NOT_EXISTS, "该邮箱存在多个账号，请联系管理员");
            }

            return doLogin(users.get(0), clientIp, ua, AuthConstants.LOGIN_TYPE_EMAIL);
        } catch (ServiceException e) {
            // ⚠️ 邮箱登录入参只有邮箱，没有账号名 —— username 列留空，
            //    把邮箱并进 msg，否则这条失败记录将完全无法定位是谁在试。
            loginLogService.recordLoginFailure(null, null, clientIp, ua[0], ua[1],
                    AuthConstants.LOGIN_TYPE_EMAIL, failureMsg(e) + "（邮箱 " + reqVO.getEmail() + "）");
            throw e;
        }
    }

    /**
     * 两种登录方式共用的后半程：校验状态 → 建会话 → 记账 → 发 token。
     */
    private LoginRespVO doLogin(SysUserDO user, String clientIp, String[] ua, Integer loginType) {
        // 角色必须在这里就算出来 —— 1007 的文案依赖「有没有分配过角色」
        Set<Long> roleIds = roleIdsOf(user.getUserId());

        checkUserStatus(user, roleIds);

        StpUtil.login(user.getUserId());
        writeSession(buildLoginUserContext(user, roleIds));
        recordLogin(user.getUserId(), clientIp);

        // 记一条登录成功日志，并把 logId 存进 Session —— 登出时靠它回填 logout_time
        // （sys_login_log 与会话之间没有天然关联，按 user_id 反查在多端登录时会串号）。
        //
        // ⚠️ 位置必须在 StpUtil.login() 之后：SaSession 是随登录建立的。
        // ⚠️ logId 为 null 说明写日志失败了（该方法吞异常），此时跳过即可 ——
        //    只是这条会话没有时长，不该影响登录本身。
        Long logId = loginLogService.recordLoginSuccess(user.getUserId(), user.getUsername(),
                clientIp, ua[0], ua[1], loginType);
        if (logId != null) {
            StpUtil.getSession().set(AuthConstants.LOGIN_LOG_ID_SESSION_KEY, logId);
        }

        // tokenTimeout 的单位是秒。⚠️ 不要硬编码 86400 ——
        // application.yml 里有 active-timeout: 1800，实际值受它影响。
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return new LoginRespVO(tokenInfo.getTokenValue(), tokenInfo.getTokenTimeout());
    }

    /**
     * 失败日志要写什么文案。
     *
     * <p>⚠️ <b>1002 必须强制用错误码自带的那句话，不能透传 {@code e.getMessage()}</b>：
     * 密码登录有意让「用户不存在」与「密码错误」返回同一个错误码 1002，以防账号枚举
     * （见 {@link #login} 里的注释）。万一将来有人给其中一条分支加了更具体的文案，
     * 日志页就会变成一个现成的账号枚举工具 —— 这里把口径钉死，不透传。
     *
     * <p>其余错误码（验证码错、账号禁用…）透传即可：它们要么与账号是否存在无关，
     * 要么发生在密码已经验过之后，不构成枚举风险。
     */
    private String failureMsg(ServiceException e) {
        if (ErrorCode.USER_PASSWORD_ERROR.getCode() == e.getCode()) {
            return ErrorCode.USER_PASSWORD_ERROR.getMessage();
        }
        return e.getMessage();
    }

    /**
     * 校验账号状态，非「正常」一律抛 1007，文案按文档 2.1 分三种情况。
     *
     * <p>判断「是否待分配角色」的依据是 <b>{@code sys_user_role} 有没有行</b>，
     * 而不是「启用中的角色是否为空」—— 角色被停用时关联行还在，
     * 那种情况属于「已被禁用」，不该提示成「待分配角色」。
     */
    private void checkUserStatus(SysUserDO user, Set<Long> roleIds) {
        if (AuthConstants.USER_STATUS_NORMAL.equals(user.getStatus())) {
            return;
        }

        String message;
        if (AuthConstants.USER_STATUS_RESIGNED.equals(user.getStatus())) {
            message = AuthConstants.MSG_ACCOUNT_RESIGNED;
        } else if (roleIds.isEmpty()) {
            // 注册后的默认态：status=2 且从未分配过角色
            message = AuthConstants.MSG_ACCOUNT_PENDING_ROLE;
        } else {
            message = AuthConstants.MSG_ACCOUNT_DISABLED;
        }

        log.info("[登录被拒] userId={} status={} 原因={}", user.getUserId(), user.getStatus(), message);
        throw new ServiceException(ErrorCode.USER_NOT_ENABLED, message);
    }

    // ========================================================================
    //  登录上下文
    // ========================================================================

    /** 查用户的角色 ID 集合 */
    private Set<Long> roleIdsOf(Long userId) {
        return sysUserRoleMapper
                .selectList(Wrappers.<SysUserRoleDO>lambdaQuery().eq(SysUserRoleDO::getUserId, userId))
                .stream()
                .map(SysUserRoleDO::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 查用户的菜单 ID 集合（经角色授权表） */
    private Set<Long> menuIdsOf(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return sysRoleMenuMapper
                .selectList(Wrappers.<SysRoleMenuDO>lambdaQuery().in(SysRoleMenuDO::getRoleId, roleIds))
                .stream()
                .map(SysRoleMenuDO::getMenuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 组装登录上下文：角色、权限、数据范围。
     *
     * <p>登录时一次性算好写进 Session，避免之后每个请求都回查这几张表。
     */
    private LoginUser buildLoginUserContext(SysUserDO user, Set<Long> roleIds) {
        List<SysRoleDO> roles = roleIds.isEmpty()
                ? List.of()
                : sysRoleMapper.selectList(Wrappers.<SysRoleDO>lambdaQuery()
                        .in(SysRoleDO::getRoleId, roleIds)
                        .eq(SysRoleDO::getStatus, AuthConstants.ROLE_STATUS_NORMAL));

        Set<String> roleKeys = roles.stream()
                .map(SysRoleDO::getRoleKey)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        boolean superAdmin = roleKeys.contains(AuthConstants.SUPER_ROLE_KEY);

        LoginUser ctx = new LoginUser();
        ctx.setUserId(user.getUserId());
        ctx.setUsername(user.getUsername());
        ctx.setRealName(user.getRealName());
        ctx.setDeptId(user.getDeptId());
        ctx.setRoleKeys(roleKeys);

        // 超管的 *:*:* 不在数据库里，必须在这里合成 ——
        // 漏了会让前端的超管判断失败、按钮全灰，而登录本身是成功的，很难定位
        ctx.setPermissions(superAdmin
                ? new LinkedHashSet<>(AuthConstants.SUPER_PERMISSIONS)
                : permissionsOf(roleIds));

        Integer dataScope = resolveDataScope(roles);
        ctx.setDataScope(dataScope);
        ctx.setDataScopeDeptIds(resolveDataScopeDeptIds(user, roles, dataScope));
        return ctx;
    }

    /**
     * 由角色授权的菜单算出权限标识集合。
     *
     * <p>注意这里取的是<b>授权集合本身</b>对应的菜单，不包含「为补全树而查出来的祖先」——
     * 否则未授权的目录 perms 会混进权限列表。
     */
    private Set<String> permissionsOf(Set<Long> roleIds) {
        Set<Long> menuIds = menuIdsOf(roleIds);
        if (menuIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return sysMenuMapper.selectList(Wrappers.<SysMenuDO>lambdaQuery()
                        .in(SysMenuDO::getMenuId, menuIds)
                        .eq(SysMenuDO::getStatus, AuthConstants.MENU_STATUS_NORMAL)
                        .orderByAsc(SysMenuDO::getMenuId))
                .stream()
                .map(SysMenuDO::getPerms)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 取该用户所有角色中「最宽」的数据范围。
     *
     * <p><b>⚠️ 是 {@code min} 而不是 {@code max}</b>：取值 1 全部 / 2 自定义 / 3 本部门 /
     * 4 本部门及以下 / 5 仅本人，<b>数值越小范围越宽</b>。
     * 取反了会直接造成越权 —— 比如「仅本人(5)」的角色会被当成「范围最大」从而看到全部数据。
     */
    private Integer resolveDataScope(List<SysRoleDO> roles) {
        return roles.stream()
                .map(SysRoleDO::getDataScope)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(DataScope.SELF);
    }

    /**
     * 算出数据范围涉及的部门集合。
     *
     * <p>只有 {@code 2 自定义}（取自 {@code sys_role_dept}）和
     * {@code 4 本部门及以下}（本部门 + 用 {@code ancestors} 前缀匹配出的子部门）需要它；
     * 其余范围用 {@code deptId} / {@code userId} 就够表达，留空集即可。
     */
    private Set<Long> resolveDataScopeDeptIds(SysUserDO user, List<SysRoleDO> roles, Integer dataScope) {
        Set<Long> deptIds = new LinkedHashSet<>();

        if (DataScope.CUSTOM.equals(dataScope)) {
            Set<Long> customRoleIds = roles.stream()
                    .filter(r -> DataScope.CUSTOM.equals(r.getDataScope()))
                    .map(SysRoleDO::getRoleId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!customRoleIds.isEmpty()) {
                sysRoleDeptMapper
                        .selectList(Wrappers.<SysRoleDeptDO>lambdaQuery()
                                .in(SysRoleDeptDO::getRoleId, customRoleIds))
                        .forEach(rd -> {
                            if (rd.getDeptId() != null) {
                                deptIds.add(rd.getDeptId());
                            }
                        });
            }
            return deptIds;
        }

        if (DataScope.DEPT_AND_CHILD.equals(dataScope) && user.getDeptId() != null) {
            deptIds.add(user.getDeptId());
            SysDeptDO dept = sysDeptMapper.selectById(user.getDeptId());
            if (dept != null) {
                // ancestors 形如 "0,1"（**不含自己**，见 sql/crm.sql 的 sys_dept 种子数据）。
                // 本部门是 2 时，直属子部门的 ancestors 恰好是 "0,1,2"，
                // 更深的后代则是 "0,1,2,X..."。
                //
                // ⚠️ 前缀必须拼成 "0,1,2"（**不带尾逗号**）再分成两个条件：
                //      eq(ancestors, "0,1,2")            → 直属子部门（这一条曾经漏掉）
                //      likeRight(ancestors, "0,1,2,")    → 更深的后代
                //    只写 likeRight("0,1,2,") 的话，SQL 是 LIKE '0,1,2,%'，而直属子部门的
                //    ancestors 就是 '0,1,2'（逗号后没有字符）—— 匹配不上。
                //    结果就是「本部门及以下」看不到本部门的组，销售总监看不到销售一组/二组/三组。
                //    这是 fail-closed 的（少看数据，不越权），所以只在功能上表现为「数据莫名少了」。
                String ancestors = StringUtils.hasText(dept.getAncestors()) ? dept.getAncestors() : "0";
                String selfPrefix = ancestors + "," + dept.getDeptId();
                sysDeptMapper.selectList(Wrappers.<SysDeptDO>lambdaQuery()
                                .and(w -> w.eq(SysDeptDO::getAncestors, selfPrefix)
                                        .or().likeRight(SysDeptDO::getAncestors, selfPrefix + ","))
                                .eq(SysDeptDO::getStatus, AuthConstants.DEPT_STATUS_NORMAL))
                        .forEach(d -> deptIds.add(d.getDeptId()));
            }
        }

        return deptIds;
    }

    /** 把登录上下文写进 Sa-Token Session（拦截器会再读出来放进 LoginUserHolder） */
    private void writeSession(LoginUser ctx) {
        SaSession session = StpUtil.getSession();
        session.set(LoginUserKeys.USERNAME, ctx.getUsername());
        session.set(LoginUserKeys.REAL_NAME, ctx.getRealName());
        session.set(LoginUserKeys.DEPT_ID, ctx.getDeptId());
        session.set(LoginUserKeys.DATA_SCOPE, ctx.getDataScope());
        session.set(LoginUserKeys.DATA_SCOPE_DEPT_IDS, ctx.getDataScopeDeptIds());
        session.set(LoginUserKeys.ROLE_KEYS, ctx.getRoleKeys());
        session.set(LoginUserKeys.PERMISSIONS, ctx.getPermissions());
    }

    /**
     * 记录最后登录时间与 IP。
     *
     * <p>用<b>局部更新</b>（只带主键 + 这两个字段），不要 {@code updateById(整个 user)} ——
     * 那会把读出来的旧 password / status 一起回写，并发时可能覆盖掉别人刚做的修改。
     * 依赖全局 {@code update-strategy: not_null}，其余 null 字段会被忽略。
     */
    private void recordLogin(Long userId, String clientIp) {
        SysUserDO update = new SysUserDO();
        update.setUserId(userId);
        update.setLastLoginTime(LocalDateTime.now());
        update.setLastLoginIp(clientIp);
        sysUserMapper.updateById(update);
    }

    // ========================================================================
    //  当前用户信息
    // ========================================================================

    @Override
    public UserInfoRespVO currentUserInfo() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUserDO user = sysUserMapper.selectById(userId);
        if (user == null) {
            // 会话还在但用户被物理删了（或逻辑删除）—— 让前端重新登录
            throw new ServiceException(ErrorCode.USER_NOT_EXISTS);
        }

        Set<Long> roleIds = roleIdsOf(userId);
        LoginUser ctx = buildLoginUserContext(user, roleIds);

        UserInfoRespVO vo = authConvert.toUserInfo(user);
        // 性别要转码（Integer 0/1/2 → 未知/男/女），所以 AuthConvert 里 ignore 掉了，
        // 在这里补。口径与 /system/user 的 UserRespVO.gender 一致。
        vo.setGender(DisplayEnumTranslator.genderToText(user.getGender()));
        vo.setDeptName(deptNameOf(user.getDeptId()));
        vo.setRoles(new ArrayList<>(ctx.getRoleKeys()));
        vo.setPermissions(new ArrayList<>(ctx.getPermissions()));
        vo.setMenus(buildMenuTree(roleIds, ctx.getRoleKeys().contains(AuthConstants.SUPER_ROLE_KEY)));
        return vo;
    }

    private String deptNameOf(Long deptId) {
        if (deptId == null) {
            return null;
        }
        SysDeptDO dept = sysDeptMapper.selectById(deptId);
        return dept == null ? null : dept.getDeptName();
    }

    /**
     * 构建下发给前端的菜单树（含目录、菜单、按钮三类节点）。
     *
     * <p>前端只用 {@code menuType=2} 的节点生成路由，用 {@code visible=0} 判断隐藏页，
     * 用 {@code perms} 填路由 meta —— 所以三类节点都要下发，交给前端过滤。
     */
    private List<MenuRespVO> buildMenuTree(Set<Long> roleIds, boolean superAdmin) {
        // 1) 取候选菜单
        List<SysMenuDO> menus;
        if (superAdmin) {
            // 超管取全量，不依赖「admin 在 sys_role_menu 里被授予了全部菜单」这条种子数据 ——
            // 将来新增菜单不必重跑授权脚本
            menus = sysMenuMapper.selectList(Wrappers.<SysMenuDO>lambdaQuery()
                    .eq(SysMenuDO::getStatus, AuthConstants.MENU_STATUS_NORMAL));
        } else {
            Set<Long> menuIds = menuIdsOf(roleIds);
            if (menuIds.isEmpty()) {
                return new ArrayList<>();   // 没有任何授权菜单 -> 空树，而不是报错
            }
            menus = sysMenuMapper.selectList(Wrappers.<SysMenuDO>lambdaQuery()
                    .in(SysMenuDO::getMenuId, menuIds)
                    .eq(SysMenuDO::getStatus, AuthConstants.MENU_STATUS_NORMAL));
        }

        Map<Long, SysMenuDO> byId = new LinkedHashMap<>();
        menus.forEach(m -> byId.put(m.getMenuId(), m));

        // 2) 补全祖先链，避免「父目录未授权 -> 子菜单变孤儿」
        fillMissingAncestors(byId);

        // 3) 组装树。先全量排序，再顺序挂接 —— 等价于每一层都有序，不用在递归里再排
        List<MenuRespVO> all = byId.values().stream()
                .sorted(Comparator
                        .comparing(SysMenuDO::getSort, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SysMenuDO::getMenuId, Comparator.nullsLast(Long::compareTo)))
                .map(authConvert::toMenuVO)
                .collect(Collectors.toList());

        Map<Long, MenuRespVO> nodeMap = new LinkedHashMap<>();
        all.forEach(n -> nodeMap.put(n.getMenuId(), n));

        List<MenuRespVO> roots = new ArrayList<>();
        for (MenuRespVO node : all) {
            MenuRespVO parent = node.getParentId() == null ? null : nodeMap.get(node.getParentId());
            if (parent == null || AuthConstants.MENU_ROOT_PARENT_ID.equals(node.getParentId())) {
                // 顶层节点，或者父节点不在集合里（孤儿）—— 提升为根，绝不丢弃
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /**
     * 向上补全缺失的祖先节点。
     *
     * <p>每轮批量 {@code in (...)} 查一层，复杂度是 O(层数 × n)；
     * 逐个节点单独查会退化成 O(n²)。
     *
     * <p>补全时同样要求 {@code status = 1}：祖先是停用的就不补，
     * 子节点随后会在组装阶段被提升为根 —— 功能可用，而不是整棵子树凭空消失。
     */
    private void fillMissingAncestors(Map<Long, SysMenuDO> byId) {
        Set<Long> missing = missingParentIds(byId);

        int depth = 0;
        while (!missing.isEmpty() && depth++ < AuthConstants.MAX_MENU_DEPTH) {
            List<SysMenuDO> parents = sysMenuMapper.selectList(Wrappers.<SysMenuDO>lambdaQuery()
                    .in(SysMenuDO::getMenuId, missing)
                    .eq(SysMenuDO::getStatus, AuthConstants.MENU_STATUS_NORMAL));
            if (parents.isEmpty()) {
                return;
            }
            parents.forEach(p -> byId.put(p.getMenuId(), p));
            missing = missingParentIds(byId);
        }
    }

    /** 当前集合里「父节点不是根、且父节点不在集合中」的那些父 ID */
    private Set<Long> missingParentIds(Map<Long, SysMenuDO> byId) {
        return byId.values().stream()
                .map(SysMenuDO::getParentId)
                .filter(pid -> pid != null
                        && !AuthConstants.MENU_ROOT_PARENT_ID.equals(pid)
                        && !byId.containsKey(pid))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // ========================================================================
    //  注册 / 密码 / 资料
    // ========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterReqVO reqVO) {
        // 1) 先查重，避免在必然失败的请求上白白烧掉一条验证码。
        //    走 ORM 而不是手写 SQL：@TableLogic 会自动带 del_flag = 0，
        //    与唯一键 uk_username(username, is_active)「只约束存活行」的语义一致 ——
        //    手写 SQL 漏了 del_flag 会把「已删除账号占用的名字」误判成已存在。
        if (sysUserMapper.exists(SysUserDO::getUsername, reqVO.getUsername())) {
            throw new ServiceException(ErrorCode.USER_USERNAME_EXISTS);
        }

        // 2) 邮箱查重。⚠️ 这一步**必须放在验码之前**，理由与上面查账号一样：
        //    验证码是一次性的，先验码再报「邮箱已注册」等于白白烧掉用户收到的那封信。
        //    这里用「已注册」这种明确提示是可以的 —— 注册接口本来就必须告诉用户能不能注册；
        //    而**发送验证码接口绝不能**做同样的判断（那会变成邮箱枚举器）。
        if (sysUserMapper.exists(SysUserDO::getEmail, reqVO.getEmail())) {
            throw new ServiceException(ErrorCode.USER_EMAIL_EXISTS);
        }

        // 3) 再验码（邮箱 + 邮箱验证码）
        emailCodeService.verifyAndConsume(reqVO.getEmail(), reqVO.getEmailCode());

        // 4) 落库
        SysUserDO user = new SysUserDO();
        user.setUsername(reqVO.getUsername());
        user.setPassword(passwordEncoder.encode(reqVO.getPassword()));
        // ⚠️ real_name 是 NOT NULL 且没有默认值，而注册请求里根本没有这个字段 ——
        //    必须补一个占位值，漏了会直接 Column 'real_name' cannot be null。
        //    先拿账号占位，管理员分配角色时会一并改掉。
        user.setRealName(reqVO.getUsername());
        user.setNickname(reqVO.getUsername());
        // 邮箱是注册时的身份凭证，同时也是之后登录 / 找回密码的凭据，必须落库。
        // 手机号不再参与注册（原先是必填），留给管理员在「人员管理」里补。
        user.setEmail(reqVO.getEmail());
        user.setGender(0);
        // 关键：注册 = 投递申请，不是开通账号。禁用 + 不分配角色
        user.setStatus(AuthConstants.USER_STATUS_DISABLED);

        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发兜底：两人同时注册同一账号/邮箱时，上面的查重都会返回 false，靠唯一键兜住。
            // 两个唯一键分别是 uk_username(username, is_active) 与 uk_email(email, is_active)，
            // 这里分不清是哪个撞了 —— 提示里把两个原因都说上，别让用户去猜。
            // 不捕的话会以 500 返回，用户看到「服务器内部错误」。
            throw new ServiceException(ErrorCode.USER_USERNAME_EXISTS,
                    "账号或邮箱已被注册，请更换后重试");
        }

        // 不写 sys_user_role（角色由公司定，不能让注册者自选），也不自动登录
        log.info("[注册] 账号已创建（禁用态，待管理员分配角色并激活）: username={} userId={}",
                reqVO.getUsername(), user.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forgotPassword(ForgotPasswordReqVO reqVO) {
        emailCodeService.verifyAndConsume(reqVO.getEmail(), reqVO.getEmailCode());

        List<SysUserDO> users = sysUserMapper.selectList(SysUserDO::getEmail, reqVO.getEmail());
        if (users.isEmpty()) {
            throw new ServiceException(ErrorCode.USER_NOT_EXISTS, "该邮箱未注册");
        }
        if (users.size() > 1) {
            // 同上：有 uk_email 唯一索引，正常不该出现多个，保留防御
            log.warn("[忘记密码] 邮箱命中多个账号: email={} count={}", reqVO.getEmail(), users.size());
            throw new ServiceException(ErrorCode.USER_NOT_EXISTS, "该邮箱存在多个账号，请联系管理员");
        }
        SysUserDO user = users.get(0);

        SysUserDO update = new SysUserDO();
        update.setUserId(user.getUserId());
        update.setPassword(passwordEncoder.encode(reqVO.getNewPassword()));
        sysUserMapper.updateById(update);

        // 密码已改，让该账号所有已登录会话立即失效（文档 2.5 的建议）。
        // ⚠️ 严格来说 kickout 应放在事务提交之后执行，否则事务回滚了
        //    「密码没改，人却被踢下线」。这里只有一个 UPDATE，风险可接受；
        //    若将来在本方法里加了更多写操作，请改用 TransactionSynchronization.afterCommit。
        StpUtil.kickout(user.getUserId());
        log.info("[忘记密码] 密码已重置并踢下线全部会话: userId={}", user.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordReqVO reqVO) {
        // 只从登录态取用户 ID，不接受任何前端传值
        Long userId = StpUtil.getLoginIdAsLong();

        SysUserDO user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException(ErrorCode.USER_NOT_EXISTS);
        }
        if (!passwordEncoder.matches(reqVO.getOldPassword(), user.getPassword())) {
            throw new ServiceException(ErrorCode.USER_OLD_PASSWORD_ERROR);
        }

        SysUserDO update = new SysUserDO();
        update.setUserId(userId);
        update.setPassword(passwordEncoder.encode(reqVO.getNewPassword()));
        sysUserMapper.updateById(update);

        // 让所有旧会话失效 —— 当前这个 token 也在其中。
        // 本次响应仍能正常返回，但紧接着的 /auth/me 会拿到 401，
        // 前端据此引导回登录页，这是预期行为。
        StpUtil.kickout(userId);
        log.info("[修改密码] 密码已更新并踢下线全部会话: userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(ProfileUpdateReqVO reqVO) {
        // userId 只从登录态取。ProfileUpdateReqVO 里结构上就没有 userId 字段，
        // 所以「改别人」这件事在这一层是不可能发生的
        Long userId = LoginUserHolder.getRequired().getUserId();

        SysUserDO update = new SysUserDO();
        update.setUserId(userId);
        update.setRealName(reqVO.getRealName());
        update.setNickname(reqVO.getNickname());
        update.setPhone(reqVO.getPhone());
        update.setEmail(reqVO.getEmail());
        update.setAvatar(reqVO.getAvatar());
        // update-strategy: not_null -> 传 null 的字段自动忽略，即「不传 = 不改」
        sysUserMapper.updateById(update);

        // 顶栏显示的 realName 来自 Session，不同步的话得重新登录才会变
        if (StringUtils.hasText(reqVO.getRealName())) {
            StpUtil.getSession().set(LoginUserKeys.REAL_NAME, reqVO.getRealName());
        }
    }

    @Override
    public void logout() {
        // ⚠️ 顺序不能反：先把 logId 从 Session 里读出来，再注销。
        //    StpUtil.logout() 之后 Session 就没了，读不到 logId，「登陆时长」永远算不出来。
        //
        // ⚠️ 这条路径**只覆盖「用户主动点退出登录」**。以下情况不会走到这里，
        //    那些登录记录的 logout_time 会一直是 NULL（前端「登陆时长」显示为空）：
        //      - 直接关浏览器 / 关标签页
        //      - 会话到期
        //      - forgotPassword / changePassword 里的 StpUtil.kickout（强制下线）
        //    这是已知取舍，不是 bug。
        Long logId = readLoginLogId();
        if (logId != null) {
            loginLogService.recordLogout(logId, LocalDateTime.now());
        }
        StpUtil.logout();
    }

    /**
     * 从 Session 读本次登录的日志ID。
     *
     * <p>读不到时返回 null 而不是抛异常 —— 本次改动之前登录的会话没有这个值，
     * 属于正常情况（用户还没重新登录过），不该让退出登录失败。
     *
     * <p>存进去的是 {@code Long}，但经 Redis(Jackson) 反序列化后类型可能是 Integer，
     * 所以按 {@code Number} 取再转，不要直接强转 Long。
     */
    private Long readLoginLogId() {
        try {
            Object v = StpUtil.getSession().get(AuthConstants.LOGIN_LOG_ID_SESSION_KEY);
            return v instanceof Number n ? n.longValue() : null;
        } catch (Exception e) {
            log.warn("[登录日志] 读取 loginLogId 失败，本次退出不记录登出时间", e);
            return null;
        }
    }
}
