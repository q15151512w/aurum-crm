package com.aurum.crm.module.crm.ai.tool.impl;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.crm.model.DictLabels;
import com.aurum.crm.module.crm.model.dto.CustomerPageReqVO;
import com.aurum.crm.module.crm.model.vo.CustomerRespVO;
import com.aurum.crm.module.crm.service.CustomerPoolService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查询客户公海（没人负责、可被领取的客户）。
 *
 * <p><b>和 {@code search_customers} 的区别要讲清楚</b>：那个查的是**在跟的**客户（有负责人），
 * 本工具查的是**公海里的**（{@code owner_user_id} 恒为空）。
 * 用户问「公海有多少客户」「有多少没人认领的客户」用这个。
 *
 * <p>⚠️ 公海内客户的负责人恒为空，所以**数据权限对它没有收窄作用**
 * （{@code CustomerPoolService.poolPage} 的注释：公海对全员可见）。
 * 能看见它的前提是功能权限 {@code crm:customerPool:list}。
 */
@Component
public class SearchCustomerPoolTool implements AiTool {

    private final CustomerPoolService customerPoolService;
    private final ToolResultShaper shaper;

    public SearchCustomerPoolTool(CustomerPoolService customerPoolService, ToolResultShaper shaper) {
        this.customerPoolService = customerPoolService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "search_customer_pool";
    }

    @Override
    public String permission() {
        return "crm:customerPool:list";
    }

    @Override
    public String description() {
        return "查询客户公海里**没人负责**的客户（可被领取的那些）。"
                + "用户问「公海有多少客户」「有多少没人认领的客户」「公海里某行业的客户」时用这个。"
                + "⚠️ 别和 search_customers 混：那个查的是在跟的客户（有负责人），这个查的是公海里的（无负责人）。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string", "关键字，匹配客户名称/负责人/联系电话"));
        // ⚠️ 码值与 sql/crm.sql 的 sys_dict_data 逐字对齐
        props.put("source", ToolArgs.prop("integer", "客户来源码值：1官网 2电话 3展会 4转介绍 5广告 6地推 7其他"));
        props.put("industry", ToolArgs.prop("integer", "所属行业码值：1制造业 2互联网 3金融 4教育 5医疗 6政府 7零售 8其他"));
        props.put("level", ToolArgs.prop("integer", "客户级别码值：1重要 2普通 3非优先"));
        props.put("pageNo", ToolArgs.prop("integer", "页码，从 1 开始，最大 5"));
        props.put("pageSize", ToolArgs.prop("integer", "每页条数，最大 10，默认 5"));
        return ToolArgs.schema(props);
    }

    @Override
    public Object execute(Map<String, Object> args) {
        CustomerPageReqVO req = new CustomerPageReqVO();
        req.setKeyword(ToolArgs.str(args, "keyword"));
        req.setSource(ToolArgs.intVal(args, "source"));
        req.setIndustry(ToolArgs.intVal(args, "industry"));
        req.setLevel(ToolArgs.intVal(args, "level"));
        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        PageResult<CustomerRespVO> page = customerPoolService.poolPage(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    private Map<String, Object> toRow(CustomerRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        row.put("customerId", vo.getId() == null ? null : String.valueOf(vo.getId()));
        row.put("customerNo", vo.getCustomerNo());
        row.put("customerName", vo.getCustomerName());
        row.put("phone", vo.getPhone());
        // 码值必须转成中文再给模型：直接给 "3" 它只能瞎猜
        row.put("source", DictLabels.source(vo.getSource()));
        row.put("industry", DictLabels.industry(vo.getIndustry()));
        row.put("level", DictLabels.level(vo.getLevel()));
        row.put("dealStatus", DictLabels.dealStatus(vo.getDealStatus()));
        row.put("region", vo.getRegion());
        // —— 公海专属字段（与 search_customers 的差别所在）——
        row.put("enterPoolReason", DictLabels.poolReason(vo.getEnterPoolReasonCode()));
        row.put("enterPoolRemark", shaper.cut(vo.getEnterPoolRemark()));
        row.put("enterPoolTime", vo.getEnterPoolTime());
        return row;
    }
}
