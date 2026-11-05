package com.aurum.crm.module.crm.ai.tool.impl.system;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.system.model.dto.UserPageReqVO;
import com.aurum.crm.module.system.model.vo.UserRespVO;
import com.aurum.crm.module.system.service.UserService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查询人员（通讯录口径的最小集）。
 *
 * <p><b>为什么只回传 6 个字段</b>：{@code sys_user} 上有全公司的手机号与邮箱，
 * 一旦原样交给模型，就等于把「全公司通讯录」通过一句自然语言问句泄出去了 ——
 * 而这类泄露没有任何业务收益：AI 助手的场景是「找人对事」，
 * 需要的是姓名 / 部门 / 角色 / 在职状态，不是联系方式。
 * 因此 {@code phone} 与 {@code email} 在这里被**显式丢弃**，
 * 不是「忘了加」，改动前请先想清楚这个口径。
 *
 * <p>⚠️ {@code sys_user} / {@code sys_dept} <b>不在数据权限白名单里</b>
 * （{@code DataPermissionInnerInterceptor.SCOPED_TABLES} 只有 crm_lead / crm_customer 等业务表），
 * 所以行级权限对本工具**不生效** —— {@link #permission()} 返回的
 * {@code system:user:list} 是唯一的闸门，不能写成 null。
 */
@Component
public class SearchUsersTool implements AiTool {

    private final UserService userService;
    private final ToolResultShaper shaper;

    public SearchUsersTool(UserService userService, ToolResultShaper shaper) {
        this.userService = userService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "search_users";
    }

    @Override
    public String permission() {
        return "system:user:list";
    }

    @Override
    public String description() {
        return "按姓名/账号关键字、部门、在职状态查询内部人员。用户问「有哪些人」「某部门有哪些人」"
                + "「某人的角色是什么」「谁离职了」时用这个。"
                + "出于隐私考虑，本工具**不返回手机号和邮箱**，问联系方式请引导用户去人员管理页查看。"
                + "需要权限 system:user:list。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string", "关键字，匹配姓名或登录账号"));
        props.put("deptId", ToolArgs.prop("string", "限定某个部门（含子部门），取自 get_dept_tree 的 deptId"));
        props.put("status", ToolArgs.enumProp(
                "账号状态（中文）", List.of("正常", "禁用", "离职")));
        props.put("noRole", ToolArgs.enumProp(
                "是否只看「还没有任何角色」的账号（注册后待管理员处理的申请）", List.of("是", "否")));
        props.put("pageNo", ToolArgs.prop("integer", "页码，从 1 开始，最大 5"));
        props.put("pageSize", ToolArgs.prop("integer", "每页条数，最大 10，默认 5"));
        return ToolArgs.schema(props);
    }

    @Override
    public Object execute(Map<String, Object> args) {
        UserPageReqVO req = new UserPageReqVO();
        req.setKeyword(ToolArgs.str(args, "keyword"));
        req.setDeptId(ToolArgs.longVal(args, "deptId"));

        // 中文标签 → 码值。ListReqVO 的 status 是数字口径（1/2/3），不是表单里的中文口径。
        String statusLabel = ToolArgs.str(args, "status");
        if (statusLabel != null) {
            req.setStatus(switch (statusLabel) {
                case "正常" -> 1;
                case "禁用" -> 2;
                case "离职" -> 3;
                default -> throw new IllegalArgumentException("状态不合法，只能是：正常 / 禁用 / 离职");
            });
        }

        req.setNoRole("是".equals(ToolArgs.str(args, "noRole")));

        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        PageResult<UserRespVO> page = userService.pageQuery(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    /**
     * 投影成一行。**只留 6 个字段**，phone / email 一律不出现在这里 ——
     * 见类注释「为什么只回传 6 个字段」。
     */
    private Map<String, Object> toRow(UserRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        row.put("userId", vo.getId() == null ? null : String.valueOf(vo.getId()));
        row.put("name", vo.getName());
        row.put("username", vo.getUsername());
        row.put("deptName", vo.getDeptName());
        row.put("roleName", vo.getRoleName());
        row.put("workStatus", vo.getWorkStatus());
        row.put("accountStatus", vo.getAccountStatus());
        return row;
    }
}
