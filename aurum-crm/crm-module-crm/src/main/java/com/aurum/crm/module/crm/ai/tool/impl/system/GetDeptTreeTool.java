package com.aurum.crm.module.crm.ai.tool.impl.system;

import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.system.model.vo.DeptTreeNodeVO;
import com.aurum.crm.module.system.service.TreeService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 查询部门（组织架构）。
 *
 * <p><b>为什么要把树拍平</b>：{@code TreeService.deptTree()} 返回的是嵌套结构，
 * 模型对嵌套 JSON 的字段引用很容易出错（「第三层的那个部门叫什么」），
 * 而拍平成「一行一个部门 + parentId + level」后，模型可以直接按行读、按 id 引用，
 * 也方便它回答「某部门下面有哪些子部门」这类问题。
 *
 * <p><b>为什么必须自己封顶</b>：这不是分页接口 —— Service 一次性返回全量部门树，
 * 部门多的公司能到几百上千行，全塞给模型既超上下文又超预算。
 * 这里硬性截断到 {@link #MAX_ROWS} 行，并把**真实总数**放进信封的 {@code total}，
 * 让模型看得出「是被截断了」而不是「一共就这些」。
 *
 * <p>⚠️ {@code sys_dept} <b>不在数据权限白名单里</b>，行级权限不生效 ——
 * {@link #permission()} 返回的 {@code system:dept:list} 是唯一闸门，不能写成 null。
 */
@Component
public class GetDeptTreeTool implements AiTool {

    /**
     * 单次返回的最大行数。
     *
     * <p>远超分页工具的 10 行 —— 组织架构是「要看清全貌才有意义」的数据，
     * 只给 10 行等于什么都答不了。200 行足够覆盖绝大多数公司的部门规模。
     */
    private static final int MAX_ROWS = 200;

    private final TreeService treeService;
    private final ToolResultShaper shaper;

    public GetDeptTreeTool(TreeService treeService, ToolResultShaper shaper) {
        this.treeService = treeService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "get_dept_tree";
    }

    @Override
    public String permission() {
        return "system:dept:list";
    }

    @Override
    public String description() {
        return "获取完整部门（组织架构）清单，已拍平成一行一个部门，含 deptId / 名称 / 类型 / 负责人 / "
                + "上级部门 parentId / 层级 level。用户问「有哪些部门」「组织架构」「某部门下面有哪些子部门」"
                + "「某部门的负责人是谁」时用这个。本工具无参数。"
                + "部门数超过 200 时会截断，信封里的 total 是真实部门数。需要权限 system:dept:list。";
    }

    @Override
    public Map<String, Object> parameters() {
        // 无参工具：显式给出空 schema，而不是返回 null（与 GetSalesFunnelTool 同款处理）
        return ToolArgs.schema(ToolArgs.props());
    }

    @Override
    public Object execute(Map<String, Object> args) {
        List<DeptTreeNodeVO> tree = treeService.deptTree();

        List<Map<String, Object>> all = new ArrayList<>();
        // 从根节点开始深度优先展开，逐层把「父部门 id」和「层级」带下去 ——
        // DeptTreeNodeVO 本身没有 parentId 字段（它是给前端树组件用的），只能在这里算出来。
        for (DeptTreeNodeVO root : tree) {
            flatten(root, null, 1, all);
        }

        long total = all.size();
        List<Map<String, Object>> rows = total > MAX_ROWS
                ? new ArrayList<>(all.subList(0, MAX_ROWS))
                : all;

        // total 传**真实部门数**而不是 rows.size()：模型看到 total > returned 才知道被截断了，
        // 否则会把「前 200 个部门」当成「全公司就 200 个部门」。
        Map<String, Object> result = shaper.envelope(total, rows);
        if (total > MAX_ROWS) {
            result.put("truncated", true);
            result.put("hint", "部门数量超过 " + MAX_ROWS + " 行已截断，只显示前 " + MAX_ROWS + " 个部门。");
        }
        return result;
    }

    /** 深度优先拍平：先输出自己，再递归子节点。 */
    private void flatten(DeptTreeNodeVO node, String parentId, int level, List<Map<String, Object>> out) {
        Map<String, Object> row = ToolResultShaper.row();
        row.put("deptId", node.getId() == null ? null : String.valueOf(node.getId()));
        row.put("name", node.getName());
        // type 是英文字符串 group/dept/team（Service 已转），不是码值
        row.put("type", node.getType());
        row.put("leaderName", node.getLeaderName());
        row.put("parentId", parentId);
        row.put("level", level);
        out.add(row);

        if (node.getChildren() != null) {
            String selfId = node.getId() == null ? null : String.valueOf(node.getId());
            for (DeptTreeNodeVO child : node.getChildren()) {
                flatten(child, selfId, level + 1, out);
            }
        }
    }
}
