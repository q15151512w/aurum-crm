package com.aurum.crm.module.crm.model.vo;

import com.aurum.crm.module.crm.model.dto.ContractItemReqVO;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 合同响应（列表 / 详情共用同一套字段名）。
 *
 * <p><b>字段名对齐前端</b>：与 {@code crm-ui} 合同列表的表格列、详情页的描述项逐项对应，
 * 前端不需要任何字段映射层。{@code id} / {@code name} 是给通用列表组件用的别名
 * （{@code useTable} 要求每行必须有 {@code id}）。
 *
 * <p><b>三类字段的口径</b>（见《05-接口文档》的字段口径约定）：
 * <ul>
 *     <li><b>数据字典字段</b>（{@code contractType}）—— 返回 TINYINT <b>码值</b>，
 *         由前端的 {@code *_CODE_OPTIONS} 映射成中文；</li>
 *     <li><b>状态机枚举字段</b>（{@code auditStatus} / {@code status}）—— 由后端转成
 *         <b>中文标签</b>（见 {@code ContractAuditStatus} / {@code ContractStatus}）；</li>
 *     <li><b>跨表派生字段</b>（{@code customerName} / {@code opportunityName} /
 *         {@code quotationNo} / {@code ownerName} / {@code creator}）—— 由 Service 批量补值，
 *         避免前端逐行再发请求。</li>
 * </ul>
 *
 * <p>⚠️ {@code finalAmount}（整单最终价，含税）映射自 {@code crm_contract.amount} ——
 * 这一列本身就是含税金额（DDL 注释：合同金额(含税,须等于明细汇总)），
 * 前端的列名口径与之一致，只是叫法不同。
 *
 * <p>⚠️ 前端列表还有一列「整单利润」，本版本**不计算利润**（全库无 profit 列，
 * 《02-建表文档》3.6 明确说明），该列已从前端移除，这里的 VO 也不提供。
 */
@Data
public class ContractRespVO {

    /**
     * 前端表格主键别名（值与 {@code contractId} 相同）。
     *
     * <p>前端通用列表组件用 {@code row.id} 做行键：详情跳转（{@code ProList.openDetail}）、
     * 勾选后的批量操作载荷（{@code useTable.selectedIds}）都读它。缺了它详情会跳到
     * {@code /crm/contract/undefined}、批量操作会发出 {@code undefined}。
     *
     * <p>⚠️ 这个别名漏了很久：{@code ContractConvert} 是**没有 @Mapping 的纯同名拷贝**，
     * 而客户 / 线索 / 商机 / 报价单的 Convert 都显式写了 {@code xxxId → id} 的映射。
     */
    private Long id;

    /** 前端表格列别名（值与 {@code contractName} 相同）：列表第一列的 prop 是 {@code name}。 */
    private String name;

    private Long contractId;
    private String contractNo;
    private String contractName;
    private Long customerId;

    /** 客户名称（跨表补值，前端列表「客户名称」列） */
    private String customerName;

    private Long opportunityId;

    /** 商机名称（跨表补值，前端列表「商机名称」列） */
    private String opportunityName;

    private Long quotationId;

    /** 报价单编号（跨表补值，前端列表「报价单编号」列） */
    private String quotationNo;

    /** 合同类型（字典 crm_contract_type，返回码值，前端映射标签） */
    private Integer contractType;

    /** 合同金额（含税，须等于明细汇总） */
    private BigDecimal amount;

    /**
     * 整单最终价（含税），前端列表列 prop。
     *
     * <p>取值与 {@code amount} 完全相同，只是为了对上前端列名而给的别名。
     */
    private BigDecimal finalAmount;

    private Long ownerUserId;
    private String ownerName;
    private Long deptId;
    private LocalDate signDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private String customerSigner;
    private String companySigner;

    /**
     * 审核状态（中文标签）。
     *
     * <p>⚠️ 前端原先把这一列叫 {@code status}，但它渲染的一直是**审核态**的中文标签
     * （未提交 / 审核中 / 审核通过 / 审核拒绝）。本次对齐把前端列名改成 {@code auditStatus}，
     * 语义不变、名实相符。取值见 {@code ContractAuditStatus}。
     */
    private String auditStatus;

    /** 合同履约状态（中文标签，草稿 / 已生效 / 已作废 / 已到期），见 {@code ContractStatus} */
    private String status;

    /**
     * 当前审批节点名称（如「部门主管审批」「财务审批」）。
     *
     * <p>⚠️ <b>只在待审核列表（{@code /crm/contract/audit-list}）回填</b>，其余列表与导出为 null
     * （`spring.jackson.default-property-inclusion: non_null`，字段直接不出现）。
     *
     * <p>为什么要它：合同审批期间 {@code auditStatus} 一直是「审核中」，列表上只有这一列时
     * 看不出「主管已审完、现在等财务」—— 每个审批人都会以为流程卡住了。
     */
    private String currentNodeName;

    /** 当前节点还在等谁（姓名列表，或签节点可能多人）；非审批中或未回填时为空 */
    private List<String> pendingApproverNames;

    /** 下一步的节点名（当前节点之后的第一个节点）；已是最后一步或未回填时为 null */
    private String nextNodeName;

    /** 下一步的**预计**审批人（姓名列表，见 {@code ContractApprovalNodeVO}）；未回填时为空 */
    private List<String> nextApproverNames;

    /**
     * 提交人 / 提交时间（审批实例的发起人与发起时间）。
     *
     * <p>⚠️ 与 {@code creator} / {@code createTime}（**建单人**与建单时间）不是一回事：
     * 单据可以是甲建的、乙提交的。合同审核页的「提交人 / 提交时间」列要的是这两个字段，
     * 它们只在审批实例存在（已提交过）时才有值。
     */
    private String applyUserName;

    private LocalDateTime applyTime;

    private String remark;

    /** 当前登录人是否已关注该合同（列表/详情回填，见 {@code ContractServiceImpl}） */
    private Boolean starred;

    /** 创建人姓名（前端列表「创建人」列，跨表补值） */
    private String creator;

    private LocalDateTime createTime;

    private List<ContractItemReqVO> items;
}
