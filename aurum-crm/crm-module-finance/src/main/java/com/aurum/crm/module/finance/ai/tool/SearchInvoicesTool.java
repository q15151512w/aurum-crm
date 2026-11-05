package com.aurum.crm.module.finance.ai.tool;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.finance.model.FinanceDictLabels;
import com.aurum.crm.module.finance.model.dto.InvoicePageReqVO;
import com.aurum.crm.module.finance.model.vo.InvoiceRespVO;
import com.aurum.crm.module.finance.service.InvoiceService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 按条件查询发票。
 *
 * <p><b>为什么这个工具写在 finance 模块、而不是和别的工具挤在 crm 模块</b>：
 * {@link AiTool} 接口在 {@code crm-module-crm}，而发票 Service 在 {@code crm-module-finance}。
 * crm 看不见 finance（依赖方向单一，反过来会成环），所以工具只能由 finance 侧实现 ——
 * finance 已经依赖 crm，拿得到接口。Spring 侧无需额外配置：{@code CrmServerApplication}
 * 位于 {@code com.aurum.crm} 包下，默认 {@code @ComponentScan} 会扫到本类，
 * {@code AiToolRegistry} 的 {@code List<AiTool>} 构造注入自然把它收进去。
 *
 * <p><b>行级权限为什么是自动的</b>：{@code fin_invoice} 在
 * {@code DataPermissionInnerInterceptor.SCOPED_TABLES} 白名单里，拦截器会在 SQL 层
 * 自动追加 {@code owner_user_id} / {@code dept_id} 条件。所以本工具复用
 * {@link InvoiceService#pageQuery} 就自动获得了行级过滤，
 * <b>千万不要为了「查得全一点」自己写 SQL 或走 JDBC</b> —— 那会整条绕过拦截器，
 * 销售专员问一句「查所有发票」就能拿到全公司的开票数据。
 *
 * <p><b>⚠️ 投影刻意排除的敏感字段</b>：{@code taxNo}（纳税人识别号）、
 * {@code bankAccount}（银行账号）、{@code invoiceAddr} / {@code invoicePhone}（开票地址/电话）。
 * 这四项是客户的开票敏感信息，{@code fin_invoice} 里存的是「选中客户时带出的快照」，
 * 与客户主数据同等敏感。模型的用途只是回答「开了多少票、开给谁、什么状态」，
 * 拿到这些字段既无用，又等于把它们复制进了一份不受客户数据权限约束的上下文。
 * 保留 {@code bankName}（仅是开户行名称，不含账号）与 {@code invoiceTitle}（抬头即公司名）。
 * 将来往投影里加字段前，先问一句「这个字段泄露出去会不会出事」。
 *
 * <p><b>码值一律转中文再给模型</b>：{@code invoiceType} / {@code auditStatus} / {@code status}
 * 在 {@link InvoiceRespVO} 里都是 Integer，直接回给模型它会当成数字猜。
 * 转换复用 {@link FinanceDictLabels}（与导出 Excel 同一份字典，不会出现「列表中文、AI 码值」的口径分裂）。
 */
@Component
public class SearchInvoicesTool implements AiTool {

    private final InvoiceService invoiceService;
    private final ToolResultShaper shaper;

    public SearchInvoicesTool(InvoiceService invoiceService, ToolResultShaper shaper) {
        this.invoiceService = invoiceService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "search_invoices";
    }

    @Override
    public String permission() {
        return "fin:invoice:list";
    }

    @Override
    public String description() {
        return "按条件分页查询发票（开票申请）。用户问「开了多少发票」「某客户/某合同的发票」"
                + "「有哪些待开票/已开票的发票」「发票号 XXX 的发票」时用这个，keyword 同时匹配申请编号和发票号。"
                + "invoiceType 是发票类型码值（1增值税专票 2增值税普票 3电子专票 4电子普票）；"
                + "auditStatus 是审核状态（0待提审 1审批中 2已通过 3已驳回）；"
                + "status 是单据状态（0待开票 1已开票 2已作废），问「实际已开出的票」时用 status=1。"
                + "金额字段：amount 不含税金额，taxAmount 税额，totalAmount 价税合计（对客户报价口径用 totalAmount）。"
                + "出于安全考虑，查询结果不含纳税人识别号、银行账号、开票地址与电话，需要这些信息请让用户去发票详情页看。"
                + "需要用户有发票列表权限（fin:invoice:list），且结果受当前登录人的数据权限限制，"
                + "看不到他人负责的发票是正常的。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string", "关键字，匹配申请编号 / 发票号"));
        props.put("customerId", ToolArgs.prop("string", "限定某个客户的发票（来自 search_customers 的 customerId）"));
        props.put("contractId", ToolArgs.prop("string", "限定某个合同的发票（来自 search_contracts 的 contractId）"));
        props.put("auditStatus", ToolArgs.prop("integer", "审核状态码值：0待提审 1审批中 2已通过 3已驳回"));
        props.put("status", ToolArgs.prop("integer", "单据状态码值：0待开票 1已开票 2已作废。问「实际已开出的票」时传 1"));
        props.put("scope", ToolArgs.enumProp(
                "取数口径。不传等同于 all（在数据权限范围内不限）",
                List.of("all", "mine", "subordinate", "starred")));
        props.put("pageNo", ToolArgs.prop("integer", "页码，从 1 开始，最大 5"));
        props.put("pageSize", ToolArgs.prop("integer", "每页条数，最大 10，默认 5"));
        return ToolArgs.schema(props);
    }

    @Override
    public Object execute(Map<String, Object> args) {
        InvoicePageReqVO req = new InvoicePageReqVO();
        req.setKeyword(ToolArgs.str(args, "keyword"));
        req.setCustomerId(ToolArgs.longVal(args, "customerId"));
        req.setContractId(ToolArgs.longVal(args, "contractId"));
        req.setAuditStatus(ToolArgs.intVal(args, "auditStatus"));
        req.setStatus(ToolArgs.intVal(args, "status"));
        req.setScope(ToolArgs.str(args, "scope"));
        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        PageResult<InvoiceRespVO> page = invoiceService.pageQuery(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    private Map<String, Object> toRow(InvoiceRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        // 雪花 ID 超过 JS 的安全整数范围（2^53-1），一律转字符串，免得模型拿到一串被舍入的数字
        row.put("invoiceId", vo.getId() == null ? null : String.valueOf(vo.getId()));
        row.put("applyNo", vo.getApplyNo());
        row.put("invoiceNo", vo.getInvoiceNo());
        row.put("customerId", vo.getCustomerId() == null ? null : String.valueOf(vo.getCustomerId()));
        row.put("customerName", vo.getCustomerName());
        row.put("contractId", vo.getContractId() == null ? null : String.valueOf(vo.getContractId()));
        // 码值转中文
        row.put("invoiceType", FinanceDictLabels.invoiceType(vo.getInvoiceType()));
        row.put("auditStatus", FinanceDictLabels.auditStatus(vo.getAuditStatus()));
        row.put("status", FinanceDictLabels.invoiceStatus(vo.getStatus()));
        // 抬头（公司名）与开户行名称保留；税号/银行账号/开票地址/电话一律不进投影，见类注释
        row.put("invoiceTitle", vo.getInvoiceTitle());
        row.put("bankName", vo.getBankName());
        row.put("amount", vo.getAmount());
        row.put("taxRate", vo.getTaxRate());
        row.put("taxAmount", vo.getTaxAmount());
        row.put("totalAmount", vo.getTotalAmount());
        row.put("applyDate", vo.getApplyDate());
        row.put("issueDate", vo.getIssueDate());
        row.put("ownerName", vo.getOwnerName());
        // 一票多回款：只给条数，不给明细。明细逐行展开会让上下文迅速膨胀，需要时让用户去详情页看
        row.put("allocationCount", vo.getAllocations() == null ? null : vo.getAllocations().size());
        row.put("createTime", vo.getCreateTime());
        row.put("remark", shaper.cut(vo.getRemark()));
        return row;
    }
}
