package com.aurum.crm.module.crm.ai.tool.impl.system;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.system.model.dto.NoticePageReqVO;
import com.aurum.crm.module.system.model.vo.NoticeRespVO;
import com.aurum.crm.module.system.service.NoticeService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查询「我的消息」（含全员公告）。
 *
 * <p><b>为什么用 {@code pageQuery} 而不是 {@code managePage}</b>：这是本工具唯一的
 * 安全性来源。{@code pageQuery} 在 Service 层**强制注入**
 * {@code receiver_id = 当前登录人 OR receiver_id IS NULL} ——
 * 也就是说，无论模型传什么参数，它最多只能看到「发给我的」和「全员广播的」，
 * 天然不可能读到别人的定向通知。{@code managePage} 是管理端视角，
 * 不带接收人条件，用它就等于把全公司的通知都交给模型了，绝不能换。
 *
 * <p><b>为什么不 override {@code permission()}</b>：{@code pageQuery} 对应的
 * 「我的消息」接口本身对所有登录用户开放（前端顶栏的消息中心人人都能看），
 * 数据范围已经由接收人条件限死，再叠一层 {@code system:notice:list}
 * （种子里只授给了超管）会造成「只有管理员能用 AI 看自己的消息」这种荒谬结果。
 * 所以这里沿用接口默认的 {@code null}：仅需登录。
 *
 * <p>⚠️ 注意 {@code null} 的正当性完全建立在「走的是 pageQuery」之上 ——
 * 一旦有人把它改成 {@code managePage}，权限闸门就同时消失了。改动前请先看清楚这一条。
 */
@Component
public class SearchNoticesTool implements AiTool {

    private final NoticeService noticeService;
    private final ToolResultShaper shaper;

    public SearchNoticesTool(NoticeService noticeService, ToolResultShaper shaper) {
        this.noticeService = noticeService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "search_notices";
    }

    @Override
    public String description() {
        return "查询**当前用户自己的**消息通知与全员公告（不含别人的定向通知）。"
                + "用户问「我有什么消息」「有什么公告」「未读消息有几条」「有没有审批提醒」时用这个。"
                + "可按关键字、通知类型、已读状态筛选；只能看到发给自己或全员广播的消息，这是服务端强制的。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string", "关键字，匹配通知标题"));
        props.put("noticeType", ToolArgs.enumProp(
                "通知类型（中文）", List.of("审批提醒", "公海回收提醒", "系统公告", "任务提醒")));
        props.put("isRead", ToolArgs.enumProp("已读状态（中文）", List.of("未读", "已读")));
        props.put("pageNo", ToolArgs.prop("integer", "页码，从 1 开始，最大 5"));
        props.put("pageSize", ToolArgs.prop("integer", "每页条数，最大 10，默认 5"));
        return ToolArgs.schema(props);
    }

    @Override
    public Object execute(Map<String, Object> args) {
        NoticePageReqVO req = new NoticePageReqVO();
        req.setKeyword(ToolArgs.str(args, "keyword"));

        // 中文标签 → 码值：1 审批提醒 / 2 公海回收提醒 / 3 系统公告 / 4 任务提醒
        String typeLabel = ToolArgs.str(args, "noticeType");
        if (typeLabel != null) {
            req.setNoticeType(switch (typeLabel) {
                case "审批提醒" -> 1;
                case "公海回收提醒" -> 2;
                case "系统公告" -> 3;
                case "任务提醒" -> 4;
                default -> throw new IllegalArgumentException(
                        "通知类型不合法，只能是：审批提醒 / 公海回收提醒 / 系统公告 / 任务提醒");
            });
        }

        // 中文标签 → 码值：0 未读 / 1 已读
        String readLabel = ToolArgs.str(args, "isRead");
        if (readLabel != null) {
            req.setIsRead(switch (readLabel) {
                case "未读" -> 0;
                case "已读" -> 1;
                default -> throw new IllegalArgumentException("已读状态不合法，只能是：未读 / 已读");
            });
        }

        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        // ⚠️ 只能用 pageQuery（服务端已注入 receiver_id = 我 OR IS NULL），
        // 换成 managePage 就等于把别人的通知也交出去了。
        PageResult<NoticeRespVO> page = noticeService.pageQuery(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    private Map<String, Object> toRow(NoticeRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        row.put("noticeId", vo.getId() == null ? null : String.valueOf(vo.getId()));
        row.put("title", vo.getTitle());
        // 通知正文可能很长，截断后再给模型
        row.put("content", shaper.cut(vo.getContent()));
        row.put("typeName", vo.getTypeName());
        row.put("isRead", vo.getIsRead() == null ? null : (vo.getIsRead() == 1 ? "已读" : "未读"));
        row.put("bizType", vo.getBizType());
        row.put("bizId", vo.getBizId() == null ? null : String.valueOf(vo.getBizId()));
        row.put("sendTime", vo.getSendTime());
        return row;
    }
}
