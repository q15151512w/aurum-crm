package com.aurum.crm.module.crm.ai.tool.impl;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.crm.model.QuotationStatus;
import com.aurum.crm.module.crm.model.dto.QuotationPageReqVO;
import com.aurum.crm.module.crm.model.vo.QuotationRespVO;
import com.aurum.crm.module.crm.service.QuotationService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 按条件查询报价单。
 *
 * <p><b>状态口径</b>：报价单状态是**状态机枚举**（不是数据字典），走「后端转中文标签」那一套
 * —— 所以 {@code status} 入参和出参都是**中文**（报价中 / 已生成合同 / 已失效），
 * 由 {@link QuotationStatus#ofLabel} 转码值，不要传 0/1/2。
 *
 * <p>⚠️ 这个枚举是**多对一归并**的：「报价中」同时涵盖库里的 0 草稿与 1 已发出，
 * 「已失效」涵盖 3 已拒绝与 4 已作废。所以按状态筛出来的条数比码值直觉上要多。
 *
 * <p>数据源是 {@code crm_quotation}，在数据权限白名单里 —— 复用 Service 即自动获得行级过滤。
 */
@Component
public class SearchQuotationsTool implements AiTool {

    private final QuotationService quotationService;
    private final ToolResultShaper shaper;

    public SearchQuotationsTool(QuotationService quotationService, ToolResultShaper shaper) {
        this.quotationService = quotationService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "search_quotations";
    }

    @Override
    public String permission() {
        return "crm:quotation:list";
    }

    @Override
    public String description() {
        return "按条件分页查询报价单。用户问「有多少报价单」「某客户的报价单」「报价中的单子」时用这个。"
                + "status 传中文（报价中 / 已生成合同 / 已失效），不要传数字码值。"
                + "注意「报价中」是把草稿和已发出归并在一起的，条数会比想象的多。"
                + "结果受当前登录人的数据权限限制。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string", "关键字，同时匹配报价单名称与商机名称"));
        props.put("customerId", ToolArgs.prop("string", "限定某个客户的报价单（来自 search_customers 的 customerId）"));
        props.put("opportunityId", ToolArgs.prop("string", "限定某个商机的报价单（来自 search_opportunities 的 opportunityId）"));
        props.put("status", ToolArgs.enumProp(
                "报价单状态（中文）", List.of("报价中", "已生成合同", "已失效")));
        props.put("scope", ToolArgs.enumProp(
                "取数口径。all/mine/subordinate/starred 是数据范围；quoting/contracted/expired 是状态胶囊。不传则不限",
                List.of("all", "mine", "subordinate", "starred", "quoting", "contracted", "expired")));
        props.put("pageNo", ToolArgs.prop("integer", "页码，从 1 开始，最大 5"));
        props.put("pageSize", ToolArgs.prop("integer", "每页条数，最大 10，默认 5"));
        return ToolArgs.schema(props);
    }

    @Override
    public Object execute(Map<String, Object> args) {
        QuotationPageReqVO req = new QuotationPageReqVO();
        req.setKeyword(ToolArgs.str(args, "keyword"));
        req.setCustomerId(ToolArgs.longVal(args, "customerId"));
        req.setOpportunityId(ToolArgs.longVal(args, "opportunityId"));
        req.setScope(ToolArgs.str(args, "scope"));

        // 中文标签 → 码值再查。传了非法值就抛异常，由编排层回灌给模型纠正。
        String statusLabel = ToolArgs.str(args, "status");
        if (statusLabel != null) {
            if (QuotationStatus.ofLabel(statusLabel) == null) {
                throw new IllegalArgumentException("报价单状态不合法，只能是：" + QuotationStatus.labelOptions());
            }
            req.setStatus(statusLabel);
        }

        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        PageResult<QuotationRespVO> page = quotationService.pageQuery(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    private Map<String, Object> toRow(QuotationRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        row.put("quotationId", vo.getId() == null ? null : String.valueOf(vo.getId()));
        row.put("quotationNo", vo.getQuotationNo());
        row.put("name", vo.getName());
        row.put("customerId", vo.getCustomerId() == null ? null : String.valueOf(vo.getCustomerId()));
        row.put("customerName", vo.getCustomerName());
        row.put("opportunityId", vo.getOpportunityId() == null ? null : String.valueOf(vo.getOpportunityId()));
        row.put("opportunityName", vo.getOpportunityName());
        // 后端已转成中文标签
        row.put("status", vo.getStatus());
        row.put("quoteDate", vo.getQuoteDate());
        row.put("validDate", vo.getValidDate());
        row.put("totalAmount", vo.getTotalAmount());
        row.put("taxAmount", vo.getTaxAmount());
        row.put("finalAmount", vo.getFinalAmount());
        row.put("discount", vo.getDiscount());
        row.put("productCount", vo.getProductCount());
        row.put("version", vo.getVersion());
        row.put("ownerName", vo.getOwnerName());
        row.put("creator", vo.getCreator());
        row.put("createTime", vo.getCreateTime());
        // ⚠️ 不投影 vo.getProducts()：明细可能几十行，会把上下文吃掉
        row.put("remark", shaper.cut(vo.getRemark()));
        return row;
    }
}
