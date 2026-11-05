package com.aurum.crm.module.crm.ai.tool.impl.system;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.system.model.dto.RolePageReqVO;
import com.aurum.crm.module.system.model.vo.RoleRespVO;
import com.aurum.crm.module.system.service.RoleService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查询角色。
 *
 * <p>角色是「权限分配」的载体，回答「有哪些角色」「某角色下有多少人」时用得上。
 * 返回的 {@code dataScope} 是**中文**（全部数据/本部门及以下/…）而不是码值 ——
 * 模型拿到「1」只会瞎猜，拿到「本部门及以下」才能正确解释「这个人能看到多少数据」。
 *
 * <p>⚠️ {@code sys_role} <b>不在数据权限白名单里</b>，行级权限不生效 ——
 * {@link #permission()} 返回的 {@code system:role:list} 是唯一闸门，不能写成 null。
 */
@Component
public class SearchRolesTool implements AiTool {

    private final RoleService roleService;
    private final ToolResultShaper shaper;

    public SearchRolesTool(RoleService roleService, ToolResultShaper shaper) {
        this.roleService = roleService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "search_roles";
    }

    @Override
    public String permission() {
        return "system:role:list";
    }

    @Override
    public String description() {
        return "分页查询角色，返回角色名称 / 标识 / 数据范围 / 该角色下的人数 / 所属角色组。"
                + "用户问「有哪些角色」「某个角色管多少人」「某人的角色能看到多少数据」时用这个。"
                + "dataScope 是中文的数据范围描述，可直接引用。需要权限 system:role:list。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string", "关键字，匹配角色名称或角色标识"));
        props.put("pageNo", ToolArgs.prop("integer", "页码，从 1 开始，最大 5"));
        props.put("pageSize", ToolArgs.prop("integer", "每页条数，最大 10，默认 5"));
        return ToolArgs.schema(props);
    }

    @Override
    public Object execute(Map<String, Object> args) {
        RolePageReqVO req = new RolePageReqVO();
        req.setKeyword(ToolArgs.str(args, "keyword"));
        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        PageResult<RoleRespVO> page = roleService.pageQuery(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    private Map<String, Object> toRow(RoleRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        row.put("roleId", vo.getId() == null ? null : String.valueOf(vo.getId()));
        row.put("name", vo.getName());
        row.put("roleKey", vo.getRoleKey());
        row.put("dataScope", vo.getDataScope());
        row.put("userCount", vo.getUserCount());
        row.put("groupName", vo.getGroupName());
        row.put("createTime", vo.getCreateTime());
        row.put("remark", shaper.cut(vo.getRemark()));
        return row;
    }
}
