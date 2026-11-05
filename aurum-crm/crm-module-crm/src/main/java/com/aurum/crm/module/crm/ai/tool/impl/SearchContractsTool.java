package com.aurum.crm.module.crm.ai.tool.impl;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.crm.model.ContractAuditStatus;
import com.aurum.crm.module.crm.model.ContractStatus;
import com.aurum.crm.module.crm.model.dto.ContractPageReqVO;
import com.aurum.crm.module.crm.model.vo.ContractRespVO;
import com.aurum.crm.module.crm.service.ContractService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 按条件查询合同。
 *
 * <p><b>注意两个状态字段的区别</b>（很容易被模型搞混，所以写进 description）：
 * {@code auditStatus} 是**审核态**（未提交/审核中/审核通过/审核拒绝），
 * {@code status} 是**业务态**（草稿/已生效/已作废/已到期）。
 * 两者在 {@code crm_contract} 里是两列，过滤时各管各的。
 *
 * <p>入参用中文标签，由枚举的 {@code ofLabel} 转码值 —— 对模型更自然，
 * 也不容易把两个状态的码值张冠李戴。
 */
@Component
public class SearchContractsTool implements AiTool {

    private final ContractService contractService;
    private final ToolResultShaper shaper;

    public SearchContractsTool(ContractService contractService, ToolResultShaper shaper) {
        this.contractService = contractService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "search_contracts";
    }

    @Override
    public String permission() {
        return "crm:contract:list";
    }

    @Override
    public String description() {
        return "按条件分页查询合同。用户问「有多少合同」「某客户的合同」「待审核的合同」「已生效的合同」时用这个。"
                + "注意 auditStatus 是审核状态（未提交/审核中/审核通过/审核拒绝），"
                + "status 是合同业务状态（草稿/已生效/已作废/已到期），两者不同，别混用。"
                + "结果受当前登录人的数据权限限制。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string", "关键字，匹配合同名称"));
        props.put("customerId", ToolArgs.prop("string", "限定某个客户的合同（来自 search_customers 的 customerId）"));
        props.put("auditStatus", ToolArgs.enumProp(
                "审核状态（中文）", List.of("未提交", "审核中", "审核通过", "审核拒绝")));
        props.put("status", ToolArgs.enumProp(
                "合同业务状态（中文）", List.of("草稿", "已生效", "已作废", "已到期")));
        props.put("pageNo", ToolArgs.prop("integer", "页码，从 1 开始，最大 5"));
        props.put("pageSize", ToolArgs.prop("integer", "每页条数，最大 10，默认 5"));
        return ToolArgs.schema(props);
    }

    @Override
    public Object execute(Map<String, Object> args) {
        ContractPageReqVO req = new ContractPageReqVO();
        req.setKeyword(ToolArgs.str(args, "keyword"));
        req.setCustomerId(ToolArgs.longVal(args, "customerId"));

        // 中文标签 → 码值。传了非法值就抛异常，由编排层回灌给模型纠正。
        String auditLabel = ToolArgs.str(args, "auditStatus");
        if (auditLabel != null) {
            ContractAuditStatus audit = ContractAuditStatus.ofLabel(auditLabel);
            if (audit == null) {
                throw new IllegalArgumentException(
                        "审核状态不合法，只能是：" + ContractAuditStatus.labelOptions());
            }
            req.setAuditStatus(audit.getCode());
        }
        String statusLabel = ToolArgs.str(args, "status");
        if (statusLabel != null) {
            ContractStatus status = ContractStatus.ofLabel(statusLabel);
            if (status == null) {
                throw new IllegalArgumentException(
                        "合同业务状态不合法，只能是：" + ContractStatus.labelOptions());
            }
            req.setStatus(status.getCode());
        }

        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        PageResult<ContractRespVO> page = contractService.pageQuery(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    private Map<String, Object> toRow(ContractRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        row.put("contractId", vo.getId() == null ? null : String.valueOf(vo.getId()));
        row.put("contractNo", vo.getContractNo());
        row.put("name", vo.getContractName());
        row.put("customerId", vo.getCustomerId() == null ? null : String.valueOf(vo.getCustomerId()));
        row.put("customerName", vo.getCustomerName());
        row.put("finalAmount", vo.getFinalAmount());
        // 这两个后端已转成中文标签
        row.put("auditStatus", vo.getAuditStatus());
        row.put("status", vo.getStatus());
        row.put("ownerName", vo.getOwnerName());
        row.put("signDate", vo.getSignDate());
        row.put("startDate", vo.getStartDate());
        row.put("endDate", vo.getEndDate());
        row.put("customerSigner", vo.getCustomerSigner());
        row.put("creator", vo.getCreator());
        row.put("createTime", vo.getCreateTime());
        row.put("remark", shaper.cut(vo.getRemark()));
        return row;
    }
}
