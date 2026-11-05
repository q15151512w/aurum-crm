package com.aurum.crm.module.crm.ai.tool.impl;

import com.aurum.crm.module.crm.ai.tool.AiTool;
import com.aurum.crm.module.crm.ai.tool.ToolArgs;
import com.aurum.crm.module.crm.ai.tool.ToolResultShaper;
import com.aurum.crm.module.crm.model.vo.ContractApprovalNodeVO;
import com.aurum.crm.module.crm.model.vo.ContractApprovalRespVO;
import com.aurum.crm.module.crm.model.vo.ContractApprovalTaskVO;
import com.aurum.crm.module.crm.service.ContractService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 查一条合同的审批进度（走到哪一步、谁审过、现在等谁）。
 *
 * <p><b>为什么需要它</b>：合同审批期间 {@code audit_status} 一直是「审核中」，
 * 而合同审核是两级流（部门主管 → 财务），只看「审核中」三个字答不出
 * 「现在该谁审」—— 这恰恰是用户最常问的问题。
 *
 * <p>实现直接复用 {@link ContractService#approval(Long)}（与前端那条接口同一份逻辑），
 * 因此同样受数据权限限制：不在可见范围内的合同会抛 2301，由编排层回灌给模型。
 */
@Component
public class GetContractApprovalTool implements AiTool {

    private final ContractService contractService;
    private final ToolResultShaper shaper;

    public GetContractApprovalTool(ContractService contractService, ToolResultShaper shaper) {
        this.contractService = contractService;
        this.shaper = shaper;
    }

    @Override
    public String name() {
        return "get_contract_approval";
    }

    @Override
    public String permission() {
        return "crm:contract:auditList";
    }

    @Override
    public String description() {
        return "查询某条合同的审批进度：走到第几步、每一步是谁审批的（已通过/已驳回/已跳过，含审批意见）、"
                + "当前节点名称、当前还在等谁审批、下一步节点与预计审批人，以及数据异常提示。"
                + "用户问「这份合同审批到哪了」「现在该谁审」「下一步谁审」「为什么还是审核中」时用这个。"
                + "注意：合同审核态「审核中」会贯穿整个流程，只有最后一个节点通过后才会变成「审核通过」，"
                + "所以回答「该谁审」必须看返回的 currentNodeName / pendingApproverNames，而不是只看状态；"
                + "回答「下一步」看 nextNodeName / nextApproverNames（那只是**预计**，组织架构或角色成员变了就会不同）。"
                + "contractId 来自 search_contracts 的返回结果，不要自己编造。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = ToolArgs.props();
        props.put("contractId", ToolArgs.prop("string",
                "合同ID（来自 search_contracts 返回的 contractId，是字符串形式的数字）"));
        return ToolArgs.schema(props, "contractId");
    }

    @Override
    public Object execute(Map<String, Object> args) {
        Long contractId = ToolArgs.requiredLong(args, "contractId");
        ContractApprovalRespVO vo = contractService.approval(contractId);

        Map<String, Object> row = ToolResultShaper.row();
        row.put("contractId", vo.getContractId() == null ? null : String.valueOf(vo.getContractId()));
        row.put("contractNo", vo.getContractNo());
        row.put("contractName", vo.getContractName());
        // 合同审核态与实例状态是两件事，分开给，别让模型自己猜（见 VO 注释）
        row.put("auditStatus", vo.getAuditStatus());
        row.put("flowName", vo.getFlowName());
        row.put("instanceStatus", vo.getInstanceStatus());
        row.put("applyUserName", vo.getApplyUserName());
        row.put("applyTime", vo.getApplyTime());
        row.put("endTime", vo.getEndTime());
        row.put("currentNodeOrder", vo.getCurrentNodeOrder());
        row.put("currentNodeName", vo.getCurrentNodeName());
        // 这一条就是「现在该谁审」的答案
        row.put("pendingApproverNames", vo.getPendingApproverNames());
        // 这一条是「下一步到谁」（预计，组织架构/角色成员会变）
        row.put("nextNodeName", vo.getNextNodeName());
        row.put("nextApproverNames", vo.getNextApproverNames());
        row.put("nodes", nodes(vo.getNodes()));
        // 演示数据里存在「标记审核中但没有审批实例」的合同，这种情况必须告诉模型，
        // 否则它会把「没有节点」当成「流程已走完」
        row.put("abnormalHint", vo.getAbnormalHint());
        return shaper.envelope(1, List.of(row));
    }

    /**
     * 节点摘要：一行一句「1.部门主管审批｜或签｜已通过｜待审批人：王总监｜已处理：张三=已办(通过：金额无误)」。
     *
     * <p>不让模型去读嵌套结构：节点多的时候它容易把某个节点的审批人算到别的节点头上，
     * 而「谁在第几步审的」正是本工具要回答的东西。
     */
    private List<String> nodes(List<ContractApprovalNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(nodes.size());
        for (ContractApprovalNodeVO node : nodes) {
            StringBuilder line = new StringBuilder()
                    .append(node.getNodeOrder()).append('.')
                    .append(node.getNodeName())
                    .append("｜").append(node.getApproveType())
                    .append("｜").append(node.getProgress());
            String pending = join(pendingNames(node));
            if (StringUtils.hasText(pending)) {
                line.append("｜待审批人：").append(pending);
            }
            // 还没到达的节点：给「预计审批人」，措辞必须带「预计」——
            // 角色成员与组织架构都会变，模型不能把它当成既成事实
            String predicted = join(node.getPredictedApproverNames());
            if (StringUtils.hasText(predicted)) {
                line.append("｜预计审批人：").append(predicted);
            }
            String handled = handled(node.getTasks());
            if (StringUtils.hasText(handled)) {
                line.append("｜已处理：").append(handled);
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * 待审批人。接口给的姓名查不到时会缺失，这里退回「用户{id}」——
     * 工具是给模型看的，「还在等谁」不能因为一个查不到姓名的账号而变成「没人待办」。
     */
    private List<String> pendingNames(ContractApprovalNodeVO node) {
        if (node.getPendingApproverNames() != null && !node.getPendingApproverNames().isEmpty()) {
            return node.getPendingApproverNames();
        }
        if (node.getTasks() == null) {
            return List.of();
        }
        // 未处理的任务 approveResult 为 null（已办/已跳过都有结果）
        return node.getTasks().stream()
                .filter(task -> task.getApproveResult() == null)
                .map(task -> "用户" + task.getApproverId())
                .toList();
    }

    /** 已处理任务的摘要：「张三=已办(通过：金额无误)」 */
    private String handled(List<ContractApprovalTaskVO> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }
        return tasks.stream()
                .filter(task -> task.getApproveResult() != null)
                .map(task -> {
                    StringBuilder one = new StringBuilder()
                            .append(StringUtils.hasText(task.getApproverName())
                                    ? task.getApproverName()
                                    : ("用户" + task.getApproverId()))
                            .append('=').append(task.getTaskStatus())
                            .append('(').append(task.getApproveResult());
                    if (StringUtils.hasText(task.getComment())) {
                        one.append('：').append(shaper.cut(task.getComment()));
                    }
                    one.append(')');
                    return one.toString();
                })
                .collect(Collectors.joining("，"));
    }

    private String join(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join("、", values);
    }
}
