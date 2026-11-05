package com.aurum.crm.module.finance.ai.tool;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.finance.model.FinanceDictLabels;
import com.aurum.crm.module.finance.model.dto.PaymentPageReqVO;
import com.aurum.crm.module.finance.model.vo.PaymentRespVO;
import com.aurum.crm.module.finance.service.PaymentService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 按条件查询回款。
 *
 * <p><b>为什么这个工具写在 finance 模块、而不是和别的工具挤在 crm 模块</b>：
 * {@link AiTool} 接口在 {@code crm-module-crm}，而回款 Service 在 {@code crm-module-finance}。
 * crm 看不见 finance（依赖方向单一，反过来会成环），所以工具只能由 finance 侧实现 ——
 * finance 已经依赖 crm，拿得到接口。Spring 侧无需额外配置：{@code CrmServerApplication}
 * 位于 {@code com.aurum.crm} 包下，默认 {@code @ComponentScan} 会扫到本类，
 * {@code AiToolRegistry} 的 {@code List<AiTool>} 构造注入自然把它收进去。
 *
 * <p><b>行级权限为什么是自动的</b>：{@code fin_payment} 在
 * {@code DataPermissionInnerInterceptor.SCOPED_TABLES} 白名单里，拦截器会在 SQL 层
 * 自动追加 {@code owner_user_id} / {@code dept_id} 条件。所以本工具复用
 * {@link PaymentService#pageQuery} 就自动获得了行级过滤，
 * <b>千万不要为了「查得全一点」自己写 SQL 或走 JDBC</b> —— 那会整条绕过拦截器，
 * 销售专员问一句「查所有回款」就能拿到全公司的账。
 *
 * <p><b>投影刻意排除的字段</b>：收款账号（{@code accountNo}）、收款账户名（{@code accountName}）、
 * 银行流水号（{@code serialNo}）。这些是敏感财务信息，进模型上下文既无助于回答
 * 「这个月回了多少钱」，又等于把对公账户信息复制到了一份不受客户数据权限约束的地方。
 *
 * <p><b>码值一律转中文再给模型</b>：{@code paymentMethod} / {@code auditStatus} / {@code status}
 * 在 {@link PaymentRespVO} 里都是 Integer，直接回给模型它会当成数字猜。
 * 转换复用 {@link FinanceDictLabels}（与导出 Excel 同一份字典，不会出现「列表中文、AI 码值」的口径分裂）。
 */
@Component
public class SearchPaymentsTool implements AiTool {

    private final PaymentService paymentService;
    private final ToolResultShaper shaper;

    public SearchPaymentsTool(PaymentService paymentService, ToolResultShaper shaper) {
        this.paymentService = paymentService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "search_payments";
    }

    @Override
    public String permission() {
        return "fin:payment:list";
    }

    @Override
    public String description() {
        return "按条件分页查询回款（收款）记录。用户问「这个月回了多少钱」「某客户/某合同回款了多少」"
                + "「有哪些还没确认的回款」「我的回款」时用这个。"
                + "auditStatus 是审核状态（0待提审 1审批中 2已通过 3已驳回），"
                + "status 是单据状态（0待确认 1已确认 2已作废），只有 status=1 才算钱已到账，两者不同别混用。"
                + "amount 是本次回款金额，unpaidAmount 是合同剩余未回款金额。"
                + "需要用户有回款列表权限（fin:payment:list），且结果受当前登录人的数据权限限制，"
                + "看不到他人负责的回款是正常的。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("keyword", ToolArgs.prop("string", "关键字，匹配回款编号"));
        props.put("customerId", ToolArgs.prop("string", "限定某个客户的回款（来自 search_customers 的 customerId）"));
        props.put("contractId", ToolArgs.prop("string", "限定某个合同的回款（来自 search_contracts 的 contractId）"));
        props.put("auditStatus", ToolArgs.prop("integer", "审核状态码值：0待提审 1审批中 2已通过 3已驳回"));
        props.put("status", ToolArgs.prop("integer", "单据状态码值：0待确认 1已确认 2已作废。问「实际到账」时传 1"));
        props.put("scope", ToolArgs.enumProp(
                "取数口径。不传等同于 all（在数据权限范围内不限）",
                List.of("all", "mine", "subordinate", "starred")));
        props.put("pageNo", ToolArgs.prop("integer", "页码，从 1 开始，最大 5"));
        props.put("pageSize", ToolArgs.prop("integer", "每页条数，最大 10，默认 5"));
        return ToolArgs.schema(props);
    }

    @Override
    public Object execute(Map<String, Object> args) {
        PaymentPageReqVO req = new PaymentPageReqVO();
        req.setKeyword(ToolArgs.str(args, "keyword"));
        req.setCustomerId(ToolArgs.longVal(args, "customerId"));
        req.setContractId(ToolArgs.longVal(args, "contractId"));
        req.setAuditStatus(ToolArgs.intVal(args, "auditStatus"));
        req.setStatus(ToolArgs.intVal(args, "status"));
        req.setScope(ToolArgs.str(args, "scope"));
        req.setPageNo(ToolArgs.pageNo(args));
        req.setPageSize(ToolArgs.pageSize(args));

        PageResult<PaymentRespVO> page = paymentService.pageQuery(req);
        List<Map<String, Object>> rows = page.getList().stream().map(this::toRow).toList();
        return shaper.envelope(page.getTotal(), rows);
    }

    private Map<String, Object> toRow(PaymentRespVO vo) {
        Map<String, Object> row = ToolResultShaper.row();
        // 雪花 ID 超过 JS 的安全整数范围（2^53-1），一律转字符串，免得模型拿到一串被舍入的数字
        row.put("paymentId", vo.getId() == null ? null : String.valueOf(vo.getId()));
        row.put("paymentNo", vo.getPaymentNo());
        row.put("customerId", vo.getCustomerId() == null ? null : String.valueOf(vo.getCustomerId()));
        row.put("customerName", vo.getCustomerName());
        row.put("contractId", vo.getContractId() == null ? null : String.valueOf(vo.getContractId()));
        row.put("contractName", vo.getContractName());
        row.put("amount", vo.getAmount());
        row.put("unpaidAmount", vo.getUnpaidAmount());
        row.put("paymentDate", vo.getPaymentDate());
        // 以下三个都是码值，必须转中文；字典里没有的脏码值会得到 null，好过给模型一个裸数字
        row.put("paymentMethod", FinanceDictLabels.paymentMethod(vo.getPaymentMethod()));
        row.put("auditStatus", FinanceDictLabels.auditStatus(vo.getAuditStatus()));
        row.put("status", FinanceDictLabels.paymentStatus(vo.getStatus()));
        // PaymentRespVO 没有 ownerName（回款列表不补负责人姓名），只能给 id；
        // 需要姓名时让模型用这个 id 去问 sys_user 侧的工具，不要在这里臆造字段
        row.put("ownerUserId", vo.getOwnerUserId() == null ? null : String.valueOf(vo.getOwnerUserId()));
        row.put("createTime", vo.getCreateTime());
        row.put("remark", shaper.cut(vo.getRemark()));
        return row;
    }
}
