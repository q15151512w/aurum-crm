package com.aurum.crm.module.crm.ai.tool.impl.system;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.system.model.dto.LoginLogPageReqVO;
import com.aurum.crm.module.system.model.vo.LoginLogRespVO;
import com.aurum.crm.module.system.service.LoginLogService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查询登录日志。
 *
 * <p>回答「谁在什么时候登录过」「某账号最近登录时间」「有没有异常 IP 登录」时用。
 *
 * <p><b>为什么 phone 可以保留、但别的字段要小心</b>：{@code LoginLogRespVO.phone}
 * 在 Service 层**已经脱敏**（{@code 138****8880}），保留它不影响合规，
 * 还能帮人确认「是不是本人」。而日志表里其余能定位到个人的原始数据
 * （会话 id、user-agent 原文）本 VO 里也没有，不用额外处理。
 *
 * <p>⚠️ {@code sys_login_log} <b>不在数据权限白名单里</b>
 * （{@code DataPermissionInnerInterceptor.SCOPED_TABLES} 全是业务表）——
 * 也就是说任何人只要拿到 {@code system:loginLog:list} 就能看**全公司**的登录记录，
 * 行级权限不会替他兜底。因此 {@link #permission()} 是唯一闸门，不能写成 null。
 */
@Component
public class SearchLoginLogsTool implements AiTool {

    private final LoginLogService loginLogService;
    private final ToolResultShaper shaper;

    public SearchLoginLogsTool(LoginLogService loginLogService, ToolResultShaper shaper) {
        this.loginLogService = loginLogService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "search_login_logs";
    }

    @Override
    public String permission() {
        return "system:loginLog:list";
    }

    @Override
    public String description() {
        return "分页查询登录日志（全公司范围），返回登陆人 / 工号 / 脱敏手机号 / 登录时间 / IP / 浏览器 / 系统 / "
                + "登录时长。用户问「谁登录过系统」「某账号最近登录是什么时候」「有没有异地登录」时用这个。"
                + "注意 duration（登陆时长）仅在用户**主动退出登录**时才有值，为空是正常现象，不是漏记。"
                + "需要权限 system:loginLog:list。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string",
                "关键字，同时匹配登陆人姓名 / 工号 / 登录 IP"));
        props.put("pageNo", ToolArgs.prop("integer", "页码，从 1 开始，最大 5"));
        props.put("pageSize", ToolArgs.prop("integer", "每页条数，最大 10，默认 5"));
        return ToolArgs.schema(props);
    }

    @Override
    public Object execute(Map<String, Object> args) {
        LoginLogPageReqVO req = new LoginLogPageReqVO();
        req.setKeyword(ToolArgs.str(args, "keyword"));
        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        PageResult<LoginLogRespVO> page = loginLogService.pageQuery(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    private Map<String, Object> toRow(LoginLogRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        row.put("userName", vo.getUserName());
        // ⚠️ 注意是 getUsername()（工号），与上面的 getUserName()（姓名）是两个字段。
        // LoginLogRespVO 里这两个访问器是手写的（Lombok 会因大小写不敏感而漏生成一个）
        row.put("username", vo.getUsername());
        // 已脱敏（138****8880），保留不构成泄露
        row.put("phone", vo.getPhone());
        row.put("loginTime", vo.getLoginTime());
        row.put("ip", vo.getIp());
        row.put("browser", vo.getBrowser());
        row.put("os", vo.getOs());
        row.put("duration", vo.getDuration());
        return row;
    }
}
