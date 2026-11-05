package com.aurum.crm.module.system.service.impl;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.excel.ExcelKit;
import com.aurum.crm.common.excel.SpreadsheetXml;
import com.aurum.crm.module.system.dal.dataobject.SysLoginLogDO;
import com.aurum.crm.module.system.dal.dataobject.SysUserDO;
import com.aurum.crm.module.system.dal.mysql.SysLoginLogMapper;
import com.aurum.crm.module.system.dal.mysql.SysUserMapper;
import com.aurum.crm.module.system.model.dto.LoginLogPageReqVO;
import com.aurum.crm.module.system.model.vo.LoginLogExportRowVO;
import com.aurum.crm.module.system.model.vo.LoginLogRespVO;
import com.aurum.crm.module.system.service.LoginLogService;
import com.aurum.crm.module.system.service.OssStorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 登录日志服务实现。
 *
 * <p><b>写入侧一律吞异常</b>：{@link #recordLoginSuccess} 等方法被登录/登出流程直接调用，
 * 它们<b>绝不能</b>把异常抛回认证链路 —— 日志表满、字段超长、DB 抖动都不该让用户登不进来。
 * 所以写入方法内部 try/catch 并 {@code log.warn}，{@link #recordLoginSuccess} 失败时返回 null。
 *
 * <p><b>写入是同步的</b>：一次 INSERT 而已，不值得为它引入异步（{@code @Async} 需要额外的
 * 线程池配置与「异步里拿不到 ThreadLocal 登录上下文」的坑）。将来若成为瓶颈再改。
 */
@Slf4j
@Service
public class LoginLogServiceImpl implements LoginLogService {

    /** 登录状态：失败 */
    private static final int STATUS_FAILURE = 0;

    /** 登录状态：成功 */
    private static final int STATUS_SUCCESS = 1;

    /** 消息列长度上限（DDL 是 VARCHAR(255)） */
    private static final int MSG_MAX_LENGTH = 255;

    /** 浏览器 / 操作系统列长度上限（DDL 是 VARCHAR(64)） */
    private static final int UA_FIELD_MAX_LENGTH = 64;

    /** 导出对象的 OSS 业务目录（与人员导出统一落在 export/ 下） */
    private static final String EXPORT_BIZ_TYPE = "export";

    /** 导出 Excel（xlsx）的 Content-Type */
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 导出 SpreadsheetML（.xml）的 Content-Type */
    private static final String XML_CONTENT_TYPE = "application/vnd.ms-excel";

    @Resource
    private SysLoginLogMapper sysLoginLogMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private OssStorageService ossStorageService;

    // ========================================================================
    //  查询
    // ========================================================================

    @Override
    public PageResult<LoginLogRespVO> pageQuery(LoginLogPageReqVO reqVO) {
        Page<SysLoginLogDO> page = sysLoginLogMapper.selectPage(reqVO, queryWrapper(reqVO));

        Map<Long, SysUserDO> users = userMapOf(page.getRecords());

        List<LoginLogRespVO> list = page.getRecords().stream()
                .map(log -> toRespVO(log, users))
                .collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public String export(LoginLogPageReqVO reqVO) {
        List<LoginLogExportRowVO> rows = buildExportRows(reqVO);
        byte[] bytes = ExcelKit.write(rows, LoginLogExportRowVO.class, "登陆日志");
        String objectKey = ossStorageService.upload(bytes, EXPORT_BIZ_TYPE, XLSX_CONTENT_TYPE, "xlsx");
        return ossStorageService.buildUrl(objectKey);
    }

    @Override
    public String exportXml(LoginLogPageReqVO reqVO) {
        List<LoginLogExportRowVO> rows = buildExportRows(reqVO);
        byte[] bytes = SpreadsheetXml.write(rows, LoginLogExportRowVO.class, "登陆日志");
        String objectKey = ossStorageService.upload(bytes, EXPORT_BIZ_TYPE, XML_CONTENT_TYPE, "xml");
        return ossStorageService.buildUrl(objectKey);
    }

    /** 导出用：复用与 pageQuery 一致的过滤条件，但不翻页，取全部匹配记录并映射成导出行 */
    private List<LoginLogExportRowVO> buildExportRows(LoginLogPageReqVO reqVO) {
        List<SysLoginLogDO> logs = sysLoginLogMapper.selectList(queryWrapper(reqVO));
        Map<Long, SysUserDO> users = userMapOf(logs);

        return logs.stream()
                .map(log -> toExportRowVO(log, users))
                .collect(Collectors.toList());
    }

    private LoginLogExportRowVO toExportRowVO(SysLoginLogDO log, Map<Long, SysUserDO> users) {
        SysUserDO user = log.getUserId() == null ? null : users.get(log.getUserId());

        LoginLogExportRowVO row = new LoginLogExportRowVO();
        row.setRealName(user == null ? null : user.getRealName());
        row.setLoginAccount(log.getUsername());
        row.setPhone(user == null ? null : maskPhone(user.getPhone()));
        row.setLoginTime(log.getLoginTime() == null ? null : log.getLoginTime().toString());
        row.setIp(log.getIp());
        row.setBrowser(log.getBrowser());
        row.setOs(log.getOs());
        BigDecimal duration = hoursBetween(log.getLoginTime(), log.getLogoutTime());
        row.setDuration(duration == null ? null : duration.toPlainString());
        return row;
    }

    private LambdaQueryWrapper<SysLoginLogDO> queryWrapper(LoginLogPageReqVO reqVO) {
        LambdaQueryWrapper<SysLoginLogDO> wrapper = Wrappers.lambdaQuery();

        if (StringUtils.hasText(reqVO.getKeyword())) {
            String keyword = reqVO.getKeyword().trim();

            // 登陆人（real_name）在 sys_user 里，日志表里没有这一列，
            // 所以先按姓名反查一批 userId，再 OR 进主查询。
            // 先查一次是为了避免用子查询 —— 项目里没有 mapper XML，注解式 SQL 写子查询更难维护。
            Set<Long> matchedUserIds = sysUserMapper
                    .selectList(Wrappers.<SysUserDO>lambdaQuery()
                            .select(SysUserDO::getUserId)
                            .like(SysUserDO::getRealName, keyword))
                    .stream()
                    .map(SysUserDO::getUserId)
                    .collect(Collectors.toSet());

            // ⚠️ 三个条件要包在一个 and(...) 里：不包的话外层可能还有别的条件，
            //    or 会把它们一起短路掉（经典的 OR 优先级坑）
            wrapper.and(w -> {
                w.like(SysLoginLogDO::getUsername, keyword)
                        .or().like(SysLoginLogDO::getIp, keyword);
                if (!matchedUserIds.isEmpty()) {
                    w.or().in(SysLoginLogDO::getUserId, matchedUserIds);
                }
            });
        }

        // 固定二级排序：log_id 兜底，保证同一秒内的多条记录翻页不串行
        // （只按 login_time 排，时间相同的行在两次查询里顺序可能不同，会出现重复/漏行）
        wrapper.orderByDesc(SysLoginLogDO::getLoginTime)
                .orderByDesc(SysLoginLogDO::getLogId);
        return wrapper;
    }

    /**
     * 按 user_id 批量取用户，供补 real_name / phone。
     *
     * <p>⚠️ 空集时返回 {@code Collections.emptyMap()} 而<b>不是</b> {@code Map.of()}：
     * 登录失败的记录 user_id 为 null，调用处会拿 null 去 get，
     * 而 {@code Map.of()} 的 get(null) 会抛 NPE（BUG-002 的 2.4 节踩过这个坑）。
     */
    private Map<Long, SysUserDO> userMapOf(List<SysLoginLogDO> logs) {
        Set<Long> userIds = logs.stream()
                .map(SysLoginLogDO::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysUserMapper
                .selectList(Wrappers.<SysUserDO>lambdaQuery().in(SysUserDO::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(SysUserDO::getUserId, Function.identity(), (a, b) -> a));
    }

    private LoginLogRespVO toRespVO(SysLoginLogDO log, Map<Long, SysUserDO> users) {
        LoginLogRespVO vo = new LoginLogRespVO();

        SysUserDO user = log.getUserId() == null ? null : users.get(log.getUserId());

        vo.setUserName(user == null ? null : user.getRealName());
        vo.setUsername(log.getUsername());
        vo.setPhone(user == null ? null : maskPhone(user.getPhone()));
        vo.setLoginTime(log.getLoginTime());
        vo.setIp(log.getIp());
        vo.setBrowser(log.getBrowser());
        vo.setOs(log.getOs());
        vo.setDuration(hoursBetween(log.getLoginTime(), log.getLogoutTime()));
        return vo;
    }

    /**
     * 登陆时长（小时，1 位小数）。任一端为空时返回 {@code null}（前端显示空）。
     *
     * <p>时钟回拨或手工改库可能让 logout 早于 login，此时按 0 处理而不是返回负数 ——
     * 负的时长在前端只会变成让人困惑的 {@code -0.3}。
     */
    private BigDecimal hoursBetween(LocalDateTime loginTime, LocalDateTime logoutTime) {
        if (loginTime == null || logoutTime == null) {
            return null;
        }
        long minutes = Duration.between(loginTime, logoutTime).toMinutes();
        if (minutes <= 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
    }

    /**
     * 手机号脱敏：{@code 13812348880} → {@code 138****8880}。
     *
     * <p>长度不足以脱敏时（脏数据）返回 {@code null} 而不是原样返回 —— 宁可这一格空着，
     * 也不要把完整的手机号漏出去。
     */
    static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return null;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    // ========================================================================
    //  写入
    // ========================================================================

    @Override
    public Long recordLoginSuccess(Long userId, String username, String clientIp,
                                   String browser, String os, Integer loginType) {
        try {
            SysLoginLogDO logDO = newLog(username, userId, clientIp, browser, os, loginType);
            logDO.setStatus(STATUS_SUCCESS);
            logDO.setMsg("登录成功");
            // logoutTime 留空，等登出时回填
            sysLoginLogMapper.insert(logDO);
            return logDO.getLogId();
        } catch (Exception e) {
            // 返回 null 而不是抛异常：调用方拿不到 logId 只会导致「登陆时长」算不出来，
            // 远好过让用户登不进来
            log.warn("[登录日志] 记录登录成功失败，userId={} username={}", userId, username, e);
            return null;
        }
    }

    @Override
    public void recordLoginFailure(String username, Long userId, String clientIp,
                                   String browser, String os, Integer loginType, String msg) {
        try {
            SysLoginLogDO logDO = newLog(username, userId, clientIp, browser, os, loginType);
            logDO.setStatus(STATUS_FAILURE);
            logDO.setMsg(truncate(msg, MSG_MAX_LENGTH));
            sysLoginLogMapper.insert(logDO);
        } catch (Exception e) {
            log.warn("[登录日志] 记录登录失败失败，username={}", username, e);
        }
    }

    @Override
    public void recordLogout(Long logId, LocalDateTime logoutTime) {
        if (logId == null) {
            // 老会话（本次改动之前登录的）Session 里没有 logId，属正常情况，不告警
            return;
        }
        try {
            SysLoginLogDO update = new SysLoginLogDO();
            update.setLogId(logId);
            update.setLogoutTime(logoutTime == null ? LocalDateTime.now() : logoutTime);
            // 局部更新（只带主键 + 这一个字段），依赖全局 update-strategy: not_null，
            // 不会把其它列覆盖成 null
            sysLoginLogMapper.updateById(update);
        } catch (Exception e) {
            log.warn("[登录日志] 回填登出时间失败，logId={}", logId, e);
        }
    }

    private SysLoginLogDO newLog(String username, Long userId, String clientIp,
                                 String browser, String os, Integer loginType) {
        SysLoginLogDO logDO = new SysLoginLogDO();
        logDO.setUsername(truncate(username, UA_FIELD_MAX_LENGTH));
        logDO.setUserId(userId);
        logDO.setIp(truncate(clientIp, UA_FIELD_MAX_LENGTH));
        logDO.setBrowser(truncate(browser, UA_FIELD_MAX_LENGTH));
        logDO.setOs(truncate(os, UA_FIELD_MAX_LENGTH));
        logDO.setLoginType(loginType);
        // 显式赋值而不是依赖 DDL 的 DEFAULT CURRENT_TIMESTAMP：
        // 用应用时间才能和 logout_time（也是应用时间）在同一个时钟下相减
        logDO.setLoginTime(LocalDateTime.now());
        return logDO;
    }

    /** 截断到列长度，防止「字符串超长」把整条日志写失败（宁可少几个字，也不要丢整条记录） */
    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
