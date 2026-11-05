package com.aurum.crm.module.crm.ai.tool.impl;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.crm.model.DictLabels;
import com.aurum.crm.module.crm.model.dto.CustomerPageReqVO;
import com.aurum.crm.module.crm.model.vo.CustomerRespVO;
import com.aurum.crm.module.crm.service.CustomerService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 按条件查询客户。
 *
 * <p>走 {@link CustomerService#pageQuery}，数据权限自动生效。
 */
@Component
public class SearchCustomersTool implements AiTool {

    private final CustomerService customerService;
    private final ToolResultShaper shaper;

    public SearchCustomersTool(CustomerService customerService, ToolResultShaper shaper) {
        this.customerService = customerService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "search_customers";
    }

    @Override
    public String permission() {
        return "crm:customer:list";
    }

    @Override
    public String description() {
        return "按条件分页查询客户。用户问「有多少客户」「我负责的客户」「已成交的客户」「找一下XX公司的客户」时用这个。"
                + "返回 total（符合条件总数）与 rows（当前页明细）。"
                + "结果受当前登录人的数据权限限制，看不到无权查看的客户是正常的。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string", "关键字，匹配客户名称/负责人/联系电话"));
        // ⚠️ 码值与 sql/crm.sql 的 sys_dict_data 逐字对齐。原先写的是
        //    「4地推 5转介绍 6广告」和行业「6零售 7建筑」—— 与字典不符，
        //    模型会照着错的映射筛选并解释结果（静默答错，不报错）。
        props.put("source", ToolArgs.prop("integer", "客户来源码值：1官网 2电话 3展会 4转介绍 5广告 6地推 7其他"));
        props.put("industry", ToolArgs.prop("integer", "客户行业码值：1制造业 2互联网 3金融 4教育 5医疗 6政府 7零售 8其他"));
        props.put("level", ToolArgs.prop("integer", "客户级别码值：1重要 2普通 3非优先"));
        props.put("dealStatus", ToolArgs.prop("integer", "成交状态：1未成交 2已成交"));
        props.put("scope", ToolArgs.enumProp(
                "取数口径。不传等同于 all（在数据权限范围内不限）",
                List.of("all", "mine", "subordinate", "starred", "dealt", "undealt")));
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
        req.setDealStatus(ToolArgs.intVal(args, "dealStatus"));
        req.setScope(ToolArgs.str(args, "scope"));
        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        PageResult<CustomerRespVO> page = customerService.pageQuery(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    private Map<String, Object> toRow(CustomerRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        row.put("customerId", vo.getCustomerId() == null ? null : String.valueOf(vo.getCustomerId()));
        row.put("customerNo", vo.getCustomerNo());
        row.put("customerName", vo.getCustomerName());
        row.put("phone", vo.getPhone());
        row.put("email", vo.getEmail());
        row.put("source", DictLabels.source(vo.getSource()));
        row.put("industry", DictLabels.industry(vo.getIndustry()));
        row.put("level", DictLabels.level(vo.getLevel()));
        row.put("dealStatus", DictLabels.dealStatus(vo.getDealStatus()));
        row.put("region", vo.getRegion());
        row.put("address", shaper.cut(vo.getAddress()));
        row.put("ownerName", vo.getOwnerName());
        row.put("lastFollowTime", vo.getLastFollowTime());
        row.put("createTime", vo.getCreateTime());
        row.put("remark", shaper.cut(vo.getRemark()));
        return row;
    }
}
