package com.aurum.crm.module.crm.ai.tool.impl;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.crm.model.dto.ProductPageReqVO;
import com.aurum.crm.module.crm.model.vo.ProductRespVO;
import com.aurum.crm.module.crm.service.ProductService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 按条件查询产品（产品目录）。
 *
 * <p>⚠️ <b>产品是全局目录，不做数据权限过滤</b>（{@code ProductService} 的类注释写明了这一点），
 * 所以不同的人查同一个产品看到的是同一份数据 —— 这跟线索/客户「销售只能看自己的」不一样，
 * 描述里要讲清楚，否则模型会错误地说「这是你名下的产品」。
 *
 * <p>⚠️ {@code costPrice}（成本价）对非授权角色会被后端置 null。这不是 bug，
 * 而是字段级权限。模型看到 null 要**如实说无权查看**，不要当成 0（系统提示词第 5 条已要求）。
 */
@Component
public class SearchProductsTool implements AiTool {

    private final ProductService productService;
    private final ToolResultShaper shaper;

    public SearchProductsTool(ProductService productService, ToolResultShaper shaper) {
        this.productService = productService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "search_products";
    }

    @Override
    public String permission() {
        return "crm:product:list";
    }

    @Override
    public String description() {
        return "按条件分页查询产品。用户问「有哪些产品」「某类产品」「产品价格」时用这个。"
                + "注意产品是**全局目录**，不属于某个人，所以结果不按负责人过滤，别说是「你名下的产品」。"
                + "costPrice（成本价）可能因为当前账号无权查看而为空，这时要如实说明无权查看，不要当成 0。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string", "关键字，匹配产品名称"));
        props.put("categoryId", ToolArgs.prop("string", "限定某个产品分类（来自 search_product_categories 的 categoryId）"));
        props.put("pageNo", ToolArgs.prop("integer", "页码，从 1 开始，最大 5"));
        props.put("pageSize", ToolArgs.prop("integer", "每页条数，最大 10，默认 5"));
        return ToolArgs.schema(props);
    }

    @Override
    public Object execute(Map<String, Object> args) {
        ProductPageReqVO req = new ProductPageReqVO();
        req.setKeyword(ToolArgs.str(args, "keyword"));
        req.setCategoryId(ToolArgs.longVal(args, "categoryId"));
        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        PageResult<ProductRespVO> page = productService.pageQuery(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    private Map<String, Object> toRow(ProductRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        row.put("productId", vo.getId() == null ? null : String.valueOf(vo.getId()));
        row.put("name", vo.getName());
        row.put("code", vo.getCode());
        row.put("categoryId", vo.getCategoryId() == null ? null : String.valueOf(vo.getCategoryId()));
        row.put("categoryName", vo.getCategoryName());
        row.put("unitName", vo.getUnitName());
        row.put("salePrice", vo.getSalePrice());
        // 成本价可能因权限为空 —— 保持 null，让模型如实说明（别用 0 顶替）
        row.put("costPrice", vo.getCostPrice());
        row.put("taxRate", vo.getTaxRate());
        row.put("onShelf", vo.getOnShelf());
        row.put("creator", vo.getCreator());
        row.put("createTime", vo.getCreateTime());
        row.put("remark", shaper.cut(vo.getRemark()));
        return row;
    }
}
