package com.aurum.crm.module.crm.ai.tool.impl;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.crm.model.dto.ProductCategoryPageReqVO;
import com.aurum.crm.module.crm.model.vo.ProductCategoryRespVO;
import com.aurum.crm.module.crm.service.ProductCategoryService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查询产品分类。
 *
 * <p>分类是**树形**的（{@code parent_id} 自关联），但这里返回的是**拍平的列表**并带上
 * {@code parentId} —— 模型对嵌套结构的利用能力有限，拍平后它能自己按 parentId 归组，
 * 而带上 parentId 又不会丢掉层级信息。这和 {@code get_dept_tree} 的处理是两套做法：
 * 那个是完整的部门树、层级更重要，所以按层级拍平；这个层级浅，直接给平表。
 *
 * <p>与产品一样，分类是**全局目录**，不做数据权限过滤。
 */
@Component
public class SearchProductCategoriesTool implements AiTool {

    private final ProductCategoryService productCategoryService;
    private final ToolResultShaper shaper;

    public SearchProductCategoriesTool(ProductCategoryService productCategoryService, ToolResultShaper shaper) {
        this.productCategoryService = productCategoryService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "search_product_categories";
    }

    @Override
    public String permission() {
        return "crm:productCategory:list";
    }

    @Override
    public String description() {
        return "查询产品分类列表。用户问「有哪些产品分类」「产品分成哪几类」时用这个。"
                + "返回的是拍平的一行行分类，每行带 parentId，你自己按它还原层级。"
                + "分类是全局目录，不属于某个人。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string", "关键字，匹配分类名称"));
        props.put("pageNo", ToolArgs.prop("integer", "页码，从 1 开始，最大 5"));
        props.put("pageSize", ToolArgs.prop("integer", "每页条数，最大 10，默认 5"));
        return ToolArgs.schema(props);
    }

    @Override
    public Object execute(Map<String, Object> args) {
        ProductCategoryPageReqVO req = new ProductCategoryPageReqVO();
        req.setKeyword(ToolArgs.str(args, "keyword"));
        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        // ⚠️ 这个方法叫 pageList 不叫 pageQuery（接口里就这么定的）
        PageResult<ProductCategoryRespVO> page = productCategoryService.pageList(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    private Map<String, Object> toRow(ProductCategoryRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        row.put("categoryId", vo.getId() == null ? null : String.valueOf(vo.getId()));
        row.put("name", vo.getName());
        row.put("code", vo.getCode());
        row.put("parentId", vo.getParentId() == null ? null : String.valueOf(vo.getParentId()));
        row.put("creator", vo.getCreator());
        row.put("createTime", vo.getCreateTime());
        row.put("remark", shaper.cut(vo.getRemark()));
        return row;
    }
}
