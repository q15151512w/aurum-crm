package com.aurum.crm.mybatis.core;

import com.aurum.crm.common.core.DataScope;
import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据权限拦截器 —— 按 {@code sys_role.data_scope} 给查询自动追加行级过滤条件。
 *
 * <p><b>规则严格照《04-权限设计文档》3.2 / 3.3 实现</b>，统一条件模板：
 *
 * <pre>
 *   AND ( owner_user_id IS NULL          -- 公海/线索池：所有人可见（决策 4）
 *      OR &lt;data_scope 条件&gt; )         -- 私海：按角色范围
 * </pre>
 *
 * <p>这个模板有个很实用的性质：对没有公海概念的表（{@code crm_contract}、{@code fin_payment} 等），
 * 其 {@code owner_user_id} 是 NOT NULL，{@code IS NULL} 恒为 false，条件自动退化成纯 scope 条件，
 * <b>不需要为两类表写两套逻辑</b>。
 *
 * <p>各 data_scope 生成的条件见 {@link #buildCondition}。
 *
 * <h3>为什么必须覆写 buildTableExpression</h3>
 *
 * <p>MyBatis-Plus 3.5.7 的 {@link DataPermissionInterceptor#buildTableExpression} 默认实现
 * <b>忽略传进来的 {@code table} 参数</b>，无条件把条件拼到查询涉及的<b>每一张表</b>上
 * （它也没有接收 {@code MultiDataPermissionHandler} 的入口）。若直接用默认行为，
 * 任何与 {@code sys_user}、{@code sys_dept} 的 JOIN 都会被拼上
 * {@code owner_user_id IS NULL OR ...} —— 轻则结果错乱，重则直接报「Unknown column」。
 *
 * <p>所以这里覆写它：拿到 {@link Table} 做白名单判断，<b>不在白名单里的表返回 {@code null}</b>。
 * 「返回 null 即不追加条件」这一点已反编译核实 —— {@code BaseMultiTableInnerInterceptor}
 * 拿到 null 后会原样返回原来的 where。
 *
 * <h3>为什么放在 crm-mybatis 而不是 crm-security</h3>
 *
 * <p>本模块只依赖 {@code crm-common}，通过 {@link LoginUserHolder} 拿登录上下文，
 * 不依赖 {@code crm-security}（避免循环依赖）。这正是后端 README 写明的依赖方向。
 */
@Slf4j
public class DataPermissionInnerInterceptor extends DataPermissionInterceptor {

    /**
     * 参与数据权限的表（《04-权限设计文档》3.4）。
     *
     * <p>用<b>白名单</b>而不是「排除系统表」的黑名单：黑名单会让将来新增的任何表
     * 自动带上条件，包括那些根本没有 {@code owner_user_id} / {@code dept_id} 列的表 ——
     * 那会直接报 SQL 错误。白名单多写一行的成本，远低于线上炸一次。
     */
    private static final Set<String> SCOPED_TABLES = Set.of(
            "crm_lead",
            "crm_customer",
            "crm_opportunity",
            "crm_quotation",
            "crm_marketing_campaign",
            "crm_sales_target",
            "crm_contract",
            "fin_payment",
            "fin_invoice",
            "crm_follow_up",
            "crm_ticket"
    );

    /** 恒不成立的条件：用在「本该限制却算不出范围」时，宁可查不到也不放行 */
    private static final String NEVER_MATCH = "1 = 0";

    /**
     * ⚠️ <b>必须传一个 {@link MultiDataPermissionHandler}，不能用无参构造。</b>
     *
     * <p>这一条是反编译 MP 3.5.7 的 {@code DataPermissionInterceptor.processSelect} 才确认的，
     * 踩过一次，写下来免得后人再踩：
     *
     * <pre>
     * 0: aload_0
     * 1: getfield  dataPermissionHandler
     * 4: ifnonnull 8
     * 7: return                    ← handler 为 null 就直接返回，
     *                                  buildTableExpression 永远轮不到被调用
     * 8: ... instanceof MultiDataPermissionHandler
     *    ├─ 是  → processSelectBody()   ← 这条路才会逐表调 buildTableExpression
     *    └─ 否  → setWhere()            ← 直接拿 handler 的两参 getSqlSegment(where, id)，
     *                                     拿不到表名、把条件无差别拼给整条语句
     * </pre>
     *
     * <p>后果：用无参构造时拦截器「看起来在工作」（MP 会打 original SQL / parse the finished SQL
     * 日志）却<b>一条条件都没加</b>，数据权限静默失效 —— 这是最危险的那种失败方式。
     *
     * <p>传进来的 handler 本身<b>不会被调用</b>（MP 走的是 {@code buildTableExpression}，
     * 而本类覆写了它）。它唯一的作用是把 MP 引到 {@code processSelectBody} 这条分支上。
     */
    public DataPermissionInnerInterceptor() {
        super(new MultiDataPermissionHandler() {
            @Override
            public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
                // 不会被调用，逻辑见本类的 buildTableExpression 覆写。
                // 真被调到说明 MP 改了实现，返回 null（=不加条件）会静默失效，
                // 所以这里抛异常把问题暴露出来，而不是悄悄放行。
                throw new IllegalStateException(
                        "不应走到这里：数据权限逻辑在 buildTableExpression 覆写里，"
                                + "请检查 MyBatis-Plus 版本是否变更了 DataPermissionInterceptor 的分支实现");
            }
        });
    }

    @Override
    public Expression buildTableExpression(Table table, Expression where, String whereSegment) {
        if (!SCOPED_TABLES.contains(table.getName().toLowerCase())) {
            return null;
        }

        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null) {
            // 没有登录上下文：登录接口本身、定时任务、/health 等。
            // 这些场景不是数据权限的适用对象，放行是有意为之，不是漏写。
            return null;
        }

        Integer dataScope = loginUser.getDataScope();
        if (DataScope.ALL.equals(dataScope)) {
            // 「全部」不追加任何条件（管理员、财务）
            return null;
        }

        // ⚠️ dataScope 为 null 时【不能】当成「全部」放行 —— 那是 fail-open。
        //    走到这里说明「有登录用户，但它的 dataScope 是空的」，属于异常态，可能来自：
        //      · 用旧版本代码登录、Session 里还没有 DATA_SCOPE 的 token
        //        （SaTokenConfig.buildLoginUser 是「Session 读不到就不设」，不会报错）
        //      · 手工构造 LoginUser 却没设 dataScope 的调用方
        //    这些情况一律按最严的「仅本人」处理。注意与本文件 inDepts() 里
        //    「可见部门集合为空 → 1=0」是同一套 fail-closed 取向，保持一致。
        //
        //    正常登录绝不会为 null：AuthServiceImpl.resolveDataScope 有 .orElse(DataScope.SELF)。
        if (dataScope == null) {
            log.warn("[数据权限] 登录用户的 dataScope 为空，按「仅本人」处理。userId={}",
                    loginUser.getUserId());
            dataScope = DataScope.SELF;
        }

        String condition = buildCondition(qualify(table), dataScope, loginUser);
        return parseCondition(condition);
    }

    /**
     * 拼查询条件。
     *
     * <p>⚠️ <b>注入面说明</b>：拼进字符串的值只有 {@code userId} / {@code deptId} / {@code deptIds}，
     * 全部来自登录时写进 Session 的 Long，<b>不含任何用户可控输入</b>。
     * 后续若要往这里加参数（比如请求里带的部门 ID），必须改成 JSqlParser 的占位符或
     * 先做白名单校验 —— 直接拼字符串会开出注入口子。
     *
     * @param col 表的限定名（可能是别名）
     */
    private String buildCondition(String col, Integer dataScope, LoginUser loginUser) {
        if (DataScope.CUSTOM.equals(dataScope) || DataScope.DEPT_AND_CHILD.equals(dataScope)) {
            // 自定义 / 本部门及以下：登录时已经算好可见部门集合（见 AuthServiceImpl.resolveDataScopeDeptIds）
            Set<Long> deptIds = loginUser.getDataScopeDeptIds();
            return inDepts(col, deptIds);
        }

        if (DataScope.DEPT.equals(dataScope)) {
            // 本部门
            return loginUser.getDeptId() == null
                    ? NEVER_MATCH
                    : "(" + col + ".owner_user_id IS NULL OR " + col + ".dept_id = " + loginUser.getDeptId() + ")";
        }

        // 5 仅本人；以及任何未知取值 —— 一律按最严处理，不认识的 scope 绝不能当成「全部」
        return loginUser.getUserId() == null
                ? NEVER_MATCH
                : "(" + col + ".owner_user_id IS NULL OR " + col + ".owner_user_id = " + loginUser.getUserId() + ")";
    }

    /**
     * 生成「可见部门集合」条件。
     *
     * <p>⚠️ <b>集合为空时必须退化成 {@code 1 = 0}（查不到），绝不能退化成「不加条件」</b> ——
     * 后者等于全表放开，是越权级 bug。宁可少看到数据，也不能因为集合没算出来就把表敞开。
     */
    private String inDepts(String col, Collection<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            log.warn("[数据权限] 可见部门集合为空，本次查询将返回空结果（dataScope=2/4 但集合没算出来，请检查登录时的 resolveDataScopeDeptIds）");
            return NEVER_MATCH;
        }
        String ids = deptIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return "(" + col + ".owner_user_id IS NULL OR " + col + ".dept_id IN (" + ids + "))";
    }

    /**
     * 取表的限定名：有别名用别名，否则用表名。
     *
     * <p>必须带前缀 —— 多表查询里裸写 {@code dept_id} 会列名歧义。
     */
    private String qualify(Table table) {
        return table.getAlias() != null ? table.getAlias().getName() : table.getName();
    }

    /**
     * 把条件字符串解析成 JSqlParser 表达式。
     *
     * <p>解析失败时<b>返回「查不到」而不是 null</b>：条件是我们自己拼的，正常情况下不会失败；
     * 真失败了说明前面拼错了，此时放行（返回 null）等于把数据全敞开，宁可失败关闭。
     */
    private Expression parseCondition(String condition) {
        try {
            return CCJSqlParserUtil.parseCondExpression(condition);
        } catch (JSQLParserException e) {
            log.error("[数据权限] 条件解析失败，本条查询将返回空结果。条件={}", condition, e);
            try {
                return CCJSqlParserUtil.parseCondExpression(NEVER_MATCH);
            } catch (JSQLParserException fatal) {
                // 「1 = 0」都解析不了说明 JSqlParser 本身出了问题，只能抛出去
                throw new IllegalStateException("数据权限条件无法解析", fatal);
            }
        }
    }
}
