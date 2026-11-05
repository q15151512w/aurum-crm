package com.aurum.crm.module.crm.ai.tool.impl.system;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.system.model.dto.OperationLogPageReqVO;
import com.aurum.crm.module.system.model.vo.OperationLogRespVO;
import com.aurum.crm.module.system.service.OperationLogService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查询操作日志。
 *
 * <p>回答「谁改了这条数据」「某模块最近有哪些操作」「有没有失败的操作」时用。
 * {@code action} 与 {@code status} 后端已转中文（新增/修改/删除、成功/失败），直接可用。
 *
 * <p><b>为什么 params 必须截短</b>：{@code params} 是操作请求的 JSON 原文。
 * 虽然后端已把密码一类字段脱敏成 {@code ****}，但它的体量仍然不可控 ——
 * 一条批量导入的请求体可能几万字符，会把上下文预算一次吃光，
 * 而模型真正需要的是「改了什么字段」这个量级的信息。
 * 所以这里过 {@link ToolResultShaper#cut(String)}，不是可选优化，是必备闸门。
 *
 * <p>⚠️ {@code sys_operation_log} <b>不在数据权限白名单里</b>，行级权限不生效 ——
 * 拿到 {@code system:operationLog:list} 的人能看**全公司**的操作记录。
 * {@link #permission()} 是唯一闸门，不能写成 null。
 */
@Component
public class SearchOperationLogsTool implements AiTool {

    private final OperationLogService operationLogService;
    private final ToolResultShaper shaper;

    public SearchOperationLogsTool(OperationLogService operationLogService, ToolResultShaper shaper) {
        this.operationLogService = operationLogService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "search_operation_logs";
    }

    @Override
    public String permission() {
        return "system:operationLog:list";
    }

    @Override
    public String description() {
        return "分页查询操作日志（全公司范围），返回操作人 / 模块 / 操作类型（新增/修改/删除…）/ 方法 / "
                + "请求参数摘要 / IP / 耗时 / 结果（成功或失败）/ 操作时间。"
                + "用户问「谁改了这个客户」「某模块最近有什么操作」「有没有操作失败」时用这个。"
                + "请求参数（params）是截断后的摘要，不足以还原完整请求体。需要权限 system:operationLog:list。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string", "关键字，同时匹配操作人姓名与模块名称"));
        props.put("pageNo", ToolArgs.prop("integer", "页码，从 1 开始，最大 5"));
        props.put("pageSize", ToolArgs.prop("integer", "每页条数，最大 10，默认 5"));
        return ToolArgs.schema(props);
    }

    @Override
    public Object execute(Map<String, Object> args) {
        OperationLogPageReqVO req = new OperationLogPageReqVO();
        req.setKeyword(ToolArgs.str(args, "keyword"));
        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        PageResult<OperationLogRespVO> page = operationLogService.pageQuery(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    private Map<String, Object> toRow(OperationLogRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        row.put("userName", vo.getUserName());
        row.put("module", vo.getModule());
        // 已转中文标签：新增 / 修改 / 删除 / …
        row.put("action", vo.getAction());
        row.put("method", vo.getMethod());
        // ⚠️ 请求参数 JSON 原文体量不可控（批量操作能到几万字符），必须截断后才能给模型
        row.put("params", shaper.cut(vo.getParams()));
        row.put("ip", vo.getIp());
        row.put("costMs", vo.getCostMs());
        // 已转中文：成功 / 失败
        row.put("status", vo.getStatus());
        row.put("operateTime", vo.getOperateTime());
        return row;
    }
}
