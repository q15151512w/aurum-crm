package com.aurum.crm.module.crm.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.mapper.ContractItemMapper;
import com.aurum.crm.module.crm.mapper.ContractMapper;
import com.aurum.crm.module.crm.mapper.CustomerMapper;
import com.aurum.crm.module.crm.mapper.CustomerMiniMapper;
import com.aurum.crm.module.crm.mapper.OpportunityMapper;
import com.aurum.crm.module.crm.mapper.QuotationMapper;
import com.aurum.crm.module.crm.mapper.SysApprovalFlowMapper;
import com.aurum.crm.module.crm.mapper.SysApprovalInstanceMapper;
import com.aurum.crm.module.crm.mapper.SysApprovalNodeMapper;
import com.aurum.crm.module.crm.mapper.SysApprovalTaskMapper;
import com.aurum.crm.module.crm.mapper.SysUserMiniMapper;
import com.aurum.crm.module.crm.mapper.SysUserRoleMiniMapper;
import com.aurum.crm.module.crm.model.ContractDO;
import com.aurum.crm.module.crm.model.ContractItemDO;
import com.aurum.crm.module.crm.model.ApprovalNodeProgress;
import com.aurum.crm.module.crm.model.ContractAuditStatus;
import com.aurum.crm.module.crm.model.CustomerDO;
import com.aurum.crm.module.crm.model.DictLabels;
import com.aurum.crm.module.crm.model.CustomerMiniDO;
import com.aurum.crm.module.crm.model.OpportunityDO;
import com.aurum.crm.module.crm.model.QuotationDO;
import com.aurum.crm.module.crm.model.StarBizType;
import com.aurum.crm.module.crm.model.SysApprovalFlowDO;
import com.aurum.crm.module.crm.model.SysApprovalInstanceDO;
import com.aurum.crm.module.crm.model.SysApprovalNodeDO;
import com.aurum.crm.module.crm.model.SysApprovalTaskDO;
import com.aurum.crm.module.crm.model.SysUserMiniDO;
import com.aurum.crm.module.crm.model.SysUserRoleMiniDO;
import com.aurum.crm.module.crm.model.convert.ContractConvert;
import com.aurum.crm.module.crm.model.dto.ContractAuditReqVO;
import com.aurum.crm.module.crm.model.dto.ContractBatchAuditReqVO;
import com.aurum.crm.module.crm.model.dto.ContractItemReqVO;
import com.aurum.crm.module.crm.model.dto.ContractPageReqVO;
import com.aurum.crm.module.crm.model.dto.ContractSaveReqVO;
import com.aurum.crm.module.crm.model.vo.ContractApprovalNodeVO;
import com.aurum.crm.module.crm.model.vo.ContractApprovalRespVO;
import com.aurum.crm.module.crm.model.vo.ContractApprovalTaskVO;
import com.aurum.crm.module.crm.model.vo.ContractExportRowVO;
import com.aurum.crm.module.crm.model.vo.ContractRespVO;
import com.aurum.crm.module.crm.service.BusinessNumberService;
import com.aurum.crm.module.crm.service.ContractService;
import com.aurum.crm.module.crm.service.StarService;
import com.aurum.crm.module.crm.support.ContractRelationSupport;
import com.aurum.crm.module.system.service.OssStorageService;
import com.aurum.crm.mybatis.core.PageParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.aurum.crm.module.crm.service.impl.ExportSupport.timeStr;

@Service
public class ContractServiceImpl implements ContractService {

    /** 列表胶囊口径（{@code scope}），与前端 FILTERS 的 value 一一对应 */
    private static final String SCOPE_ALL = "all";
    private static final String SCOPE_MINE = "mine";
    private static final String SCOPE_SUBORDINATE = "subordinate";
    private static final String SCOPE_STARRED = "starred";

    /** 下属遍历的最大层级，防御脏数据造成的超长链（正常也就 3-4 层） */
    private static final int MAX_SUBORDINATE_DEPTH = 20;

    // ==================== 审批引擎的常量 ====================
    //
    // ⚠️ 状态码（0/1/2/3 这些）在 submit / audit 里仍是字面量，本轮**刻意不动**：
    //    那两处是已回归过的并发敏感代码，顺手替换没有收益、只有风险。
    //    真要统一，应当连同 crm-module-bpm 的抽取一起做（见下方「审批进度查询的内部方法」）。

    /** 审批实例的业务类型（与 {@code ContractServiceImpl#submit} 写库时一致） */
    private static final String BIZ_TYPE_CONTRACT = "contract";

    /** 合同审批流模板编码 */
    private static final String FLOW_CODE_CONTRACT_AUDIT = "CONTRACT_AUDIT";

    /** 审批实例进行中（sys_approval_instance.status） */
    private static final int INSTANCE_RUNNING = 0;

    /** 任务待办（sys_approval_task.task_status） */
    private static final int TASK_PENDING = 0;

    /** 审批结果：通过 / 驳回（sys_approval_task.approve_result） */
    private static final int RESULT_APPROVED = 1;
    private static final int RESULT_REJECTED = 2;

    /** 合同审核态：审核中（crm_contract.audit_status） */
    private static final int AUDIT_AUDITING = 1;

    /** 节点审批人类型（sys_approval_node.approver_type），与 {@code DictLabels#approverType} 的码值一致 */
    private static final int APPROVER_USER = 1;
    private static final int APPROVER_ROLE = 2;
    private static final int APPROVER_DEPT_LEADER = 3;
    private static final int APPROVER_SUPERIOR = 4;
    private static final int APPROVER_SELF = 5;

    @Resource private ContractMapper contractMapper;
    @Resource private ContractItemMapper contractItemMapper;
    @Resource private CustomerMapper customerMapper;
    @Resource private CustomerMiniMapper customerMiniMapper;
    @Resource private OpportunityMapper opportunityMapper;
    @Resource private QuotationMapper quotationMapper;
    @Resource private SysUserMiniMapper sysUserMiniMapper;
    @Resource private SysUserRoleMiniMapper sysUserRoleMiniMapper;
    @Resource private ContractConvert contractConvert;
    @Resource private BusinessNumberService businessNumberService;
    @Resource private SysApprovalFlowMapper sysApprovalFlowMapper;
    @Resource private SysApprovalNodeMapper sysApprovalNodeMapper;
    @Resource private SysApprovalInstanceMapper sysApprovalInstanceMapper;
    @Resource private SysApprovalTaskMapper sysApprovalTaskMapper;
    @Resource private OssStorageService ossStorageService;
    @Resource private StarService starService;
    /** 客户 / 商机 / 报价单三者的一致性校验与对齐（口径集中在那里，create 与 update 共用） */
    @Resource private ContractRelationSupport contractRelationSupport;

    @Override
    public PageResult<ContractRespVO> pageQuery(ContractPageReqVO req) {
        return page(req, false);
    }

    @Override
    public PageResult<ContractRespVO> auditPage(ContractPageReqVO req) {
        req.setAuditStatus(1);
        return page(req, true);
    }

    @Override
    public String export(ContractPageReqVO req) {
        return ExportSupport.toUrl(ossStorageService, buildExportRows(req), ContractExportRowVO.class);
    }

    @Override
    public String exportXml(ContractPageReqVO req) {
        return ExportSupport.toXmlUrl(ossStorageService, buildExportRows(req), ContractExportRowVO.class);
    }

    private List<ContractExportRowVO> buildExportRows(ContractPageReqVO req) {
        LambdaQueryWrapper<ContractDO> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(req.getKeyword())) {
            wrapper.and(w -> w.like(ContractDO::getContractName, req.getKeyword())
                    .or().like(ContractDO::getContractNo, req.getKeyword()));
        }
        wrapper.eq(req.getContractName() != null, ContractDO::getContractName, req.getContractName())
                .eq(req.getCustomerId() != null, ContractDO::getCustomerId, req.getCustomerId())
                .eq(req.getOpportunityId() != null, ContractDO::getOpportunityId, req.getOpportunityId())
                .eq(req.getQuotationId() != null, ContractDO::getQuotationId, req.getQuotationId())
                .eq(req.getOwnerUserId() != null, ContractDO::getOwnerUserId, req.getOwnerUserId())
                .eq(req.getAuditStatus() != null, ContractDO::getAuditStatus, req.getAuditStatus())
                .eq(req.getStatus() != null, ContractDO::getStatus, req.getStatus());
        // 导出复用列表的胶囊口径（scope）；数据权限仍由拦截器兜底
        if (!applyScope(wrapper, req.getScope())) {
            return List.of();
        }
        wrapper.orderByDesc(ContractDO::getCreateTime);
        List<ContractDO> records = contractMapper.selectList(wrapper);
        Map<Long, String> owners = userNames(records.stream().map(ContractDO::getOwnerUserId).toList());
        Map<Long, String> customers = customerNames(records);

        return records.stream().map(c -> {
            ContractExportRowVO row = new ContractExportRowVO();
            row.setContractNo(c.getContractNo());
            row.setContractName(c.getContractName());
            row.setCustomerName(customers.get(c.getCustomerId()));
            row.setContractType(DictLabels.contractType(c.getContractType()));
            row.setAmount(c.getAmount() == null ? "" : c.getAmount().toPlainString());
            row.setOwnerName(owners.get(c.getOwnerUserId()));
            row.setSignDate(dateStr(c.getSignDate()));
            row.setStartDate(dateStr(c.getStartDate()));
            row.setEndDate(dateStr(c.getEndDate()));
            row.setAuditStatus(DictLabels.auditStatus(c.getAuditStatus()));
            row.setStatus(DictLabels.contractStatus(c.getStatus()));
            row.setCreateTime(timeStr(c.getCreateTime()));
            return row;
        }).collect(Collectors.toList());
    }

    private String dateStr(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    @Override
    public ContractRespVO get(Long id) {
        ContractDO contract = getContract(id);
        ContractRespVO vo = contractConvert.toVO(contract);
        vo.setOwnerName(ownerName(contract.getOwnerUserId()));
        vo.setCreator(userName(contract.getCreateBy()));
        vo.setCustomerName(customerName(contract.getCustomerId()));
        vo.setOpportunityName(opportunityName(contract.getOpportunityId()));
        vo.setQuotationNo(quotationNo(contract.getQuotationId()));
        vo.setStarred(starService.isStarred(StarBizType.CONTRACT, id));
        vo.setItems(contractItemMapper.selectList(ContractItemDO::getContractId, id).stream()
                .map(contractConvert::toItemReq).toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ContractSaveReqVO req) {
        checkCustomer(req.getCustomerId());
        // 客户 / 商机 / 报价单三者的一致性校验 + 商机对齐，口径见 ContractRelationSupport。
        // create 与 update 都必须过这一关 —— 只在新增时校验的话，「改合同」就是一个绕过口子。
        Long opportunityId = contractRelationSupport.alignAndValidate(
                req.getCustomerId(), req.getOpportunityId(), req.getQuotationId());
        LoginUser user = LoginUserHolder.getRequired();
        ContractDO contract = contractConvert.toDO(req);
        contract.setOpportunityId(opportunityId);
        contract.setContractId(IdUtil.getSnowflakeNextId());
        Map<String, Object> numberContext = new HashMap<>();
        numberContext.put("customerId", req.getCustomerId());
        if (user.getDeptId() != null) numberContext.put("deptId", user.getDeptId());
        contract.setContractNo(businessNumberService.next("CONTRACT", numberContext));
        contract.setOwnerUserId(req.getOwnerUserId() == null ? user.getUserId() : req.getOwnerUserId());
        contract.setDeptId(user.getDeptId());
        contract.setAuditStatus(0);
        contract.setStatus(0);
        List<ContractItemDO> items = buildItems(req.getItems(), contract.getContractId());
        contract.setAmount(sum(items));
        contractMapper.insert(contract);
        items.forEach(contractItemMapper::insert);
        return contract.getContractId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ContractSaveReqVO req) {
        ContractDO old = getContract(id);
        editable(old);
        checkCustomer(req.getCustomerId());
        // 与 create 同一处校验（少了它，「编辑」就成了绕过一致性检查的入口）
        Long opportunityId = contractRelationSupport.alignAndValidate(
                req.getCustomerId(), req.getOpportunityId(), req.getQuotationId());
        ContractDO update = contractConvert.toDO(req);
        update.setOpportunityId(opportunityId);
        update.setContractId(id);
        update.setOwnerUserId(req.getOwnerUserId() == null ? old.getOwnerUserId() : req.getOwnerUserId());
        update.setDeptId(old.getDeptId());
        update.setAuditStatus(old.getAuditStatus());
        update.setStatus(old.getStatus());
        List<ContractItemDO> items = buildItems(req.getItems(), id);
        update.setAmount(sum(items));
        contractMapper.updateById(update);
        contractItemMapper.delete(Wrappers.lambdaQuery(ContractItemDO.class)
                .eq(ContractItemDO::getContractId, id));
        items.forEach(contractItemMapper::insert);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ContractDO contract = getContract(id);
        editable(contract);
        contractMapper.deleteById(id);
        contractItemMapper.delete(Wrappers.lambdaQuery(ContractItemDO.class)
                .eq(ContractItemDO::getContractId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        ContractDO contract = getContract(id);
        if (!Integer.valueOf(0).equals(contract.getStatus())
                || !(Integer.valueOf(0).equals(contract.getAuditStatus())
                || Integer.valueOf(3).equals(contract.getAuditStatus()))) {
            throw new ServiceException(ErrorCode.CONTRACT_AUDIT_STATUS_ERROR, "只有草稿或驳回合同可以提交审核");
        }
        List<ContractItemDO> items = contractItemMapper.selectList(ContractItemDO::getContractId, id);
        if (items.isEmpty() || sum(items).compareTo(contract.getAmount()) != 0) {
            throw new ServiceException(ErrorCode.CONTRACT_AMOUNT_MISMATCH);
        }
        SysApprovalFlowDO flow = sysApprovalFlowMapper.selectOne(
                Wrappers.lambdaQuery(SysApprovalFlowDO.class)
                        .eq(SysApprovalFlowDO::getFlowCode, FLOW_CODE_CONTRACT_AUDIT)
                        .eq(SysApprovalFlowDO::getStatus, 1));
        if (flow == null) throw new ServiceException(ErrorCode.APPROVAL_FLOW_NOT_EXISTS);
        List<SysApprovalNodeDO> nodes = sysApprovalNodeMapper.selectList(
                Wrappers.lambdaQuery(SysApprovalNodeDO.class).eq(SysApprovalNodeDO::getFlowId, flow.getFlowId())
                        .orderByAsc(SysApprovalNodeDO::getNodeOrder));
        if (nodes.isEmpty()) throw new ServiceException(ErrorCode.APPROVAL_FLOW_NOT_EXISTS, "合同审批流没有配置节点");

        // ⚠️ 顺序：**先原子抢占合同，再建审批实例与待办任务** —— 不能反过来。
        //
        //    上面 213-218 那次读只是给用户一句友好报错，它拦不住并发：两个请求可以同时读到
        //    「草稿」，然后双双往下走。真正决定胜负的是这一次 CAS —— 只有把 audit_status
        //    从「草稿(0) / 驳回(3)」改成「审批中(1)」成功的那个请求才继续，后到的那个
        //    影响 0 行、直接抛错返回，因此不会留下第二套审批实例和待办任务。
        //
        //    为什么不能只靠最后那次 updateById：它是按主键更新的，WHERE 里只有 contract_id，
        //    两个请求都会「成功」。DB 也兜不住 —— sys_approval_instance 上只有非唯一的
        //    idx_biz (biz_type, biz_id, status)，见 sql/crm.sql。
        //
        //    同样的写法在 QuotationServiceImpl#convertToContract / #voidQuotation 里已有先例。
        int claimed = contractMapper.update(null, Wrappers.<ContractDO>lambdaUpdate()
                .eq(ContractDO::getContractId, id)
                .eq(ContractDO::getStatus, 0)
                .in(ContractDO::getAuditStatus, 0, 3)
                .set(ContractDO::getAuditStatus, 1));
        if (claimed == 0) {
            throw new ServiceException(ErrorCode.CONTRACT_AUDIT_STATUS_ERROR,
                    "合同状态已被他人变更，请刷新后重试");
        }

        LoginUser user = LoginUserHolder.getRequired();
        SysApprovalInstanceDO instance = new SysApprovalInstanceDO();
        instance.setInstanceId(IdUtil.getSnowflakeNextId());
        instance.setFlowId(flow.getFlowId());
        instance.setBizType(BIZ_TYPE_CONTRACT);
        instance.setBizId(id);
        instance.setBizNo(contract.getContractNo());
        instance.setTitle(contract.getContractName());
        instance.setApplyUserId(user.getUserId());
        instance.setApplyDeptId(user.getDeptId());
        instance.setCurrentNodeOrder(nodes.get(0).getNodeOrder());
        instance.setStatus(0);
        instance.setStartTime(LocalDateTime.now());
        sysApprovalInstanceMapper.insert(instance);
        createTasks(instance, nodes.get(0), user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, ContractAuditReqVO req) {
        if (req.getResult() == null || (req.getResult() != 1 && req.getResult() != 2)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "审核结果必须为1通过或2驳回");
        }
        ContractDO contract = getContract(id);
        if (!Integer.valueOf(1).equals(contract.getAuditStatus())) {
            throw new ServiceException(ErrorCode.CONTRACT_AUDIT_STATUS_ERROR, "合同当前不在审批中");
        }
        Long userId = LoginUserHolder.getRequired().getUserId();
        SysApprovalTaskDO task = sysApprovalTaskMapper.selectOne(
                Wrappers.lambdaQuery(SysApprovalTaskDO.class)
                        .eq(SysApprovalTaskDO::getApproverId, userId)
                        .eq(SysApprovalTaskDO::getTaskStatus, 0)
                        .eq(SysApprovalTaskDO::getInstanceId,
                                currentInstanceId(id)));
        if (task == null) throw new ServiceException(ErrorCode.APPROVAL_NOT_YOUR_TASK);
        // ⚠️ 认领任务必须是原子 CAS，不能写 updateById(task)。
        //
        //    updateById 是按主键更新的，WHERE 里只有 task_id —— 同一个审批人双击「通过」时，
        //    两个事务都会读到 task_status=0、都会「成功」。各自的后续再读（下面 291-294 行）
        //    都会看到「本节点没有待办任务了」（每个事务都读到自己刚写进去的 1），于是双双越过
        //    314 行的 anyMatch 判断、双双执行 createTasks，把下一节点的任务插两遍。
        //    之后该节点一旦有人审批，265 行那个没有 LIMIT 的 selectOne 就会撞上
        //    TooManyResultsException —— 那个节点从此再也审不动（只能改库）。
        //
        //    DB 也拦不住：sys_approval_task 上只有非唯一的 idx_dodo / idx_instance。
        //    同样的「条件 UPDATE + 影响行数判断」模式见 QuotationServiceImpl 的状态流转。
        int claimed = sysApprovalTaskMapper.update(null, Wrappers.<SysApprovalTaskDO>lambdaUpdate()
                .eq(SysApprovalTaskDO::getTaskId, task.getTaskId())
                .eq(SysApprovalTaskDO::getTaskStatus, 0)
                .set(SysApprovalTaskDO::getTaskStatus, 1)
                .set(SysApprovalTaskDO::getApproveResult, req.getResult())
                .set(SysApprovalTaskDO::getComment, req.getComment())
                .set(SysApprovalTaskDO::getHandleTime, LocalDateTime.now()));
        if (claimed == 0) {
            // 已经被别人（或自己的另一次点击）处理掉了：后面任何写操作都不能再执行
            throw new ServiceException(ErrorCode.APPROVAL_TASK_HANDLED);
        }
        // 本地对象同步成已处理状态，供下面读取 approveType / approverId 等字段
        task.setTaskStatus(1);
        task.setApproveResult(req.getResult());
        task.setComment(req.getComment());
        task.setHandleTime(LocalDateTime.now());
        SysApprovalInstanceDO instance = sysApprovalInstanceMapper.selectById(task.getInstanceId());
        if (Integer.valueOf(2).equals(req.getResult())) {
            instance.setStatus(2);
            instance.setEndTime(LocalDateTime.now());
            sysApprovalInstanceMapper.updateById(instance);
            ContractDO update = new ContractDO();
            update.setContractId(id);
            update.setAuditStatus(3);
            contractMapper.updateById(update);
            return;
        }
        if (Integer.valueOf(1).equals(task.getApproveType())) {
            skipOtherTodoTasks(instance.getInstanceId(), instance.getCurrentNodeOrder(), task.getTaskId());
        }
        List<SysApprovalTaskDO> currentTasks = sysApprovalTaskMapper.selectList(
                Wrappers.lambdaQuery(SysApprovalTaskDO.class)
                        .eq(SysApprovalTaskDO::getInstanceId, instance.getInstanceId())
                        .eq(SysApprovalTaskDO::getNodeOrder, instance.getCurrentNodeOrder()));
        LoginUser applicant = applicantOf(instance);
        if (Integer.valueOf(3).equals(task.getApproveType())) {
            SysApprovalNodeDO currentNode = sysApprovalNodeMapper.selectOne(
                    Wrappers.lambdaQuery(SysApprovalNodeDO.class)
                            .eq(SysApprovalNodeDO::getFlowId, instance.getFlowId())
                            .eq(SysApprovalNodeDO::getNodeOrder, instance.getCurrentNodeOrder()));
            if (currentNode != null) {
                List<Long> approverIds = resolveApprovers(currentNode, applicant);
                List<Long> handledApproverIds = currentTasks.stream()
                        .map(SysApprovalTaskDO::getApproverId).toList();
                Long nextApproverId = approverIds.stream()
                        .filter(approverId -> !handledApproverIds.contains(approverId))
                        .findFirst().orElse(null);
                if (nextApproverId != null) {
                    createTask(instance, currentNode, nextApproverId);
                    return;
                }
            }
        }
        if (currentTasks.stream().anyMatch(t -> Integer.valueOf(0).equals(t.getTaskStatus()))) return;
        List<SysApprovalNodeDO> nodes = sysApprovalNodeMapper.selectList(
                Wrappers.lambdaQuery(SysApprovalNodeDO.class).eq(SysApprovalNodeDO::getFlowId, instance.getFlowId())
                        .orderByAsc(SysApprovalNodeDO::getNodeOrder));
        SysApprovalNodeDO next = nodes.stream()
                .filter(n -> n.getNodeOrder() > instance.getCurrentNodeOrder())
                .findFirst().orElse(null);
        if (next == null) {
            instance.setStatus(1);
            instance.setEndTime(LocalDateTime.now());
            sysApprovalInstanceMapper.updateById(instance);
            ContractDO update = new ContractDO();
            update.setContractId(id);
            update.setAuditStatus(2);
            update.setStatus(1);
            contractMapper.updateById(update);
        } else {
            instance.setCurrentNodeOrder(next.getNodeOrder());
            sysApprovalInstanceMapper.updateById(instance);
            createTasks(instance, next, applicant);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAudit(ContractBatchAuditReqVO req) {
        ContractAuditReqVO auditReq = new ContractAuditReqVO();
        auditReq.setResult(req.getResult());
        auditReq.setComment(req.getComment());
        for (Long contractId : req.getContractIds()) {
            audit(contractId, auditReq);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidContract(Long id) {
        ContractDO contract = getContract(id);
        if (!Integer.valueOf(0).equals(contract.getStatus())
                || !Integer.valueOf(0).equals(contract.getAuditStatus())
                && !Integer.valueOf(3).equals(contract.getAuditStatus())) {
            throw new ServiceException(ErrorCode.CONTRACT_AUDIT_STATUS_ERROR, "审核中、已通过或已生效合同不允许作废");
        }
        ContractDO update = new ContractDO();
        update.setContractId(id);
        update.setStatus(2);
        contractMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void star(Long id) {
        getContract(id); // 不存在的合同不能关注 → 2301
        starService.star(StarBizType.CONTRACT, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unstar(Long id) {
        getContract(id); // 与 delete 一致，先做存在性校验
        starService.unstar(StarBizType.CONTRACT, id);
    }

    /**
     * 审批进度查询 —— 回答「走到哪一步了、现在该谁审」。
     *
     * <p><b>为什么需要它</b>：合同审批期间 {@code audit_status} 一直是「审核中」，而
     * {@code CONTRACT_AUDIT} 是两级流（部门主管 → 财务）。主管审完只是推进实例、
     * 合同那一列不变，于是审批人 / 发起人都看不出球在谁手上，误以为「审批没生效」。
     *
     * <p>只读，不写任何表；合同不存在或不在数据权限内由 {@code getContract} 抛 2301。
     */
    @Override
    public ContractApprovalRespVO approval(Long id) {
        ContractDO contract = getContract(id);

        // ⚠️ 同一合同可能有【多条】实例（驳回后重新提交会新建，历史实例保留），
        //    这里只取最新一条。orderByDesc + LIMIT 1 不能省：没有 LIMIT 的 selectOne
        //    遇到多行会抛 TooManyResultsException（BUG-014 的坑）。
        SysApprovalInstanceDO instance = sysApprovalInstanceMapper.selectOne(
                Wrappers.lambdaQuery(SysApprovalInstanceDO.class)
                        .eq(SysApprovalInstanceDO::getBizType, BIZ_TYPE_CONTRACT)
                        .eq(SysApprovalInstanceDO::getBizId, id)
                        .orderByDesc(SysApprovalInstanceDO::getStartTime)
                        .last("LIMIT 1"));

        // 有实例就按实例当时的模板渲染；没有实例（草稿 / 演示数据遗留）就按当前启用的模板，
        // 让人至少知道这条合同要走几步。两者都取不到时 nodes 为空数组，不抛错。
        SysApprovalFlowDO flow = instance == null
                ? enabledContractFlow()
                : sysApprovalFlowMapper.selectById(instance.getFlowId());
        List<SysApprovalNodeDO> templateNodes = flow == null ? List.of() : nodesOfFlow(flow.getFlowId());
        List<SysApprovalTaskDO> tasks = instance == null ? List.of()
                : sysApprovalTaskMapper.selectList(
                        Wrappers.lambdaQuery(SysApprovalTaskDO.class)
                                .eq(SysApprovalTaskDO::getInstanceId, instance.getInstanceId()));

        Map<Long, String> names = userNames(taskApproverIds(tasks, instance));
        // 任务按节点号分组（TreeMap：遍历顺序稳定，输出顺序可预测；最终顺序由 approvalNodes 排序决定）
        Map<Integer, List<SysApprovalTaskDO>> tasksByNode = tasks.stream()
                .filter(task -> task.getNodeOrder() != null)
                .collect(Collectors.groupingBy(SysApprovalTaskDO::getNodeOrder, TreeMap::new, Collectors.toList()));

        boolean running = instance != null && Integer.valueOf(INSTANCE_RUNNING).equals(instance.getStatus());
        // 「下一步到谁」：只预测 currentNodeOrder 之后的节点（还没建任务的节点），
        // 与列表页共用同一份预测实现（predictApprovers）。
        Map<Integer, List<String>> predictedByNode = running && flow != null && flow.getFlowId() != null
                ? predictedOf(predictApprovers(List.of(instance),
                        Map.of(flow.getFlowId(), templateNodes)), instance.getInstanceId())
                : Map.of();
        SysApprovalNodeDO nextNode = running ? nextNode(templateNodes, instance.getCurrentNodeOrder()) : null;

        ContractApprovalRespVO vo = new ContractApprovalRespVO();
        vo.setContractId(contract.getContractId());
        vo.setContractNo(contract.getContractNo());
        vo.setContractName(contract.getContractName());
        // 合同审核态复用列表/详情那一套中文标签，口径必须一致
        vo.setAuditStatus(ContractAuditStatus.labelOf(contract.getAuditStatus()));
        vo.setFlowName(flow == null ? null : flow.getFlowName());
        if (instance != null) {
            vo.setInstanceId(instance.getInstanceId());
            vo.setInstanceStatus(DictLabels.approvalInstanceStatus(instance.getStatus()));
            vo.setApplyUserName(names.get(instance.getApplyUserId()));
            vo.setApplyTime(instance.getStartTime());
            vo.setEndTime(instance.getEndTime());
        }

        if (running) {
            Integer currentOrder = instance.getCurrentNodeOrder();
            List<SysApprovalTaskDO> currentNodeTasks = tasksByNode.get(currentOrder);
            vo.setCurrentNodeOrder(currentOrder);
            // 节点名优先取模板；模板把这一步删掉了就退回任务上的 node_name 快照
            SysApprovalNodeDO currentNode = templateNodes.stream()
                    .filter(node -> Objects.equals(node.getNodeOrder(), currentOrder))
                    .findFirst().orElse(null);
            vo.setCurrentNodeName(currentNode != null
                    ? currentNode.getNodeName() : taskNodeName(currentNodeTasks));
            vo.setPendingApproverNames(pendingApproverNames(currentNodeTasks, names));
        } else {
            vo.setPendingApproverNames(List.of());
        }

        if (nextNode != null) {
            vo.setNextNodeOrder(nextNode.getNodeOrder());
            vo.setNextNodeName(nextNode.getNodeName());
            vo.setNextApproverNames(predictedNames(predictedByNode, nextNode.getNodeOrder()));
        } else {
            vo.setNextApproverNames(List.of()); // 已是最后一步 / 流程未进行：明确给空数组，别给 null
        }

        vo.setNodes(approvalNodes(templateNodes, tasksByNode, names, predictedByNode));
        vo.setAbnormalHint(abnormalHint(contract, instance));
        return vo;
    }

    private PageResult<ContractRespVO> page(ContractPageReqVO req, boolean audit) {
        LambdaQueryWrapper<ContractDO> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(req.getKeyword())) {
            wrapper.and(w -> w.like(ContractDO::getContractName, req.getKeyword())
                    .or().like(ContractDO::getContractNo, req.getKeyword()));
        }
        wrapper.eq(req.getContractName() != null, ContractDO::getContractName, req.getContractName())
                .eq(req.getCustomerId() != null, ContractDO::getCustomerId, req.getCustomerId())
                .eq(req.getOpportunityId() != null, ContractDO::getOpportunityId, req.getOpportunityId())
                .eq(req.getQuotationId() != null, ContractDO::getQuotationId, req.getQuotationId())
                .eq(req.getOwnerUserId() != null, ContractDO::getOwnerUserId, req.getOwnerUserId())
                .eq(req.getAuditStatus() != null, ContractDO::getAuditStatus, req.getAuditStatus())
                .eq(req.getStatus() != null, ContractDO::getStatus, req.getStatus());
        // 胶囊口径（scope）—— 在数据权限之内再收窄，两者取交集。
        // 审核列表（audit=true）是审批人的待审视图，不设胶囊口径。
        if (!audit && !applyScope(wrapper, req.getScope())) {
            return emptyResult(req);
        }
        wrapper.orderByDesc(ContractDO::getCreateTime);
        Page<ContractDO> page = contractMapper.selectPage(req, wrapper);
        List<ContractDO> records = page.getRecords();
        Map<Long, String> owners = userNames(records.stream().map(ContractDO::getOwnerUserId).toList());
        Map<Long, String> creators = userNames(records.stream().map(ContractDO::getCreateBy).toList());
        Map<Long, String> customers = customerNames(records);
        Map<Long, String> opportunities = opportunityNames(records);
        Map<Long, String> quotations = quotationNos(records);
        // 批量查关注态，避免逐行查库；空页时 starredBizIds 返回空集
        List<Long> contractIds = records.stream().map(ContractDO::getContractId)
                .filter(Objects::nonNull).toList();
        Set<Long> starredIds = starService.starredBizIds(StarBizType.CONTRACT, contractIds);
        List<ContractRespVO> list = records.stream().map(c -> {
            ContractRespVO vo = contractConvert.toVO(c);
            vo.setOwnerName(owners.get(c.getOwnerUserId()));
            vo.setCreator(creators.get(c.getCreateBy()));
            vo.setCustomerName(customers.get(c.getCustomerId()));
            vo.setOpportunityName(opportunities.get(c.getOpportunityId()));
            vo.setQuotationNo(quotations.get(c.getQuotationId()));
            vo.setStarred(starredIds.contains(c.getContractId()));
            return vo;
        }).toList();
        // 待审核列表是审批人唯一能看到「轮到谁」的地方：把当前节点与待办人一并回填，
        // 否则列表上只有一句「审核中」，主管审完后再看还以为流程没动。
        // 只在这个视图回填 —— 主列表多打 3 次查询换不来任何信息。
        if (audit) {
            fillAuditProgress(list, contractIds);
        }
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    /**
     * 胶囊口径过滤（{@code scope}），与数据权限拦截器是<b>交集</b>关系 ——
     * 用户主动选的取数口径只能在数据权限允许的范围内继续收窄。
     *
     * <p>只追加裸的 {@code eq} / {@code in} 连接词，绝不追加裸 {@code or}；
     * 需要 OR 的场景都已包在各自的 {@code Consumer} 里。
     *
     * @return {@code false} 表示结果必然为空，调用方应直接返回空页 ——
     * 这同时是「不给 MyBatis-Plus 传空集合」的守卫。
     */
    private boolean applyScope(LambdaQueryWrapper<ContractDO> wrapper, String scope) {
        if (!StringUtils.hasText(scope) || SCOPE_ALL.equals(scope)) {
            return true;
        }
        LoginUser me = LoginUserHolder.getRequired();
        switch (scope) {
            case SCOPE_MINE -> {
                if (me.getUserId() == null) {
                    return false;
                }
                wrapper.eq(ContractDO::getOwnerUserId, me.getUserId());
            }
            case SCOPE_SUBORDINATE -> {
                List<Long> subordinateIds = listSubordinateUserIds(me.getUserId());
                if (subordinateIds.isEmpty()) {
                    return false;
                }
                wrapper.in(ContractDO::getOwnerUserId, subordinateIds);
            }
            case SCOPE_STARRED -> {
                Set<Long> starredIds = starService.starredBizIds(StarBizType.CONTRACT);
                if (starredIds.isEmpty()) {
                    return false;
                }
                wrapper.in(ContractDO::getContractId, starredIds);
            }
            // 未登记的取值不静默放行，直接 400 更容易发现前端漏配
            default -> throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "不支持的 scope 取值：" + scope);
        }
        return true;
    }

    /**
     * 沿 {@code sys_user.superior_id} 自顶向下逐层收集「我」的全部下属（多层，<b>不含自己</b>）。
     * 与 {@code LeadServiceImpl#listSubordinateUserIds} / {@code CustomerServiceImpl#listSubordinateUserIds} 同构。
     */
    private List<Long> listSubordinateUserIds(Long rootUserId) {
        if (rootUserId == null) {
            return List.of();
        }
        Set<Long> visited = new HashSet<>();
        visited.add(rootUserId);
        List<Long> result = new ArrayList<>();
        List<Long> frontier = List.of(rootUserId);
        for (int depth = 0; depth < MAX_SUBORDINATE_DEPTH && !frontier.isEmpty(); depth++) {
            List<SysUserMiniDO> children =
                    sysUserMiniMapper.selectList(SysUserMiniDO::getSuperiorId, frontier);
            List<Long> next = new ArrayList<>();
            for (SysUserMiniDO child : children) {
                if (child.getUserId() != null && visited.add(child.getUserId())) {
                    result.add(child.getUserId());
                    next.add(child.getUserId());
                }
            }
            frontier = next;
        }
        return result;
    }

    /** 分页空结果：筛选条件失效不该中断查询 */
    private <T> PageResult<T> emptyResult(PageParam reqVO) {
        Page<?> page = reqVO.toPage();
        return PageResult.empty((int) page.getCurrent(), (int) page.getSize());
    }

    private void createTasks(SysApprovalInstanceDO instance, SysApprovalNodeDO node, LoginUser applicant) {
        List<Long> approverIds = resolveApprovers(node, applicant);
        if (approverIds.isEmpty()) {
            throw new ServiceException(ErrorCode.APPROVAL_FLOW_NOT_EXISTS, "审批节点没有解析出审批人：" + node.getNodeName());
        }
        if (Integer.valueOf(3).equals(node.getApproveType())) {
            createTask(instance, node, approverIds.get(0));
            return;
        }
        for (Long approverId : approverIds) {
            createTask(instance, node, approverId);
        }
    }

    private void createTask(SysApprovalInstanceDO instance, SysApprovalNodeDO node, Long approverId) {
        SysApprovalTaskDO task = new SysApprovalTaskDO();
        task.setTaskId(IdUtil.getSnowflakeNextId());
        task.setInstanceId(instance.getInstanceId());
        task.setNodeId(node.getNodeId());
        task.setNodeOrder(node.getNodeOrder());
        task.setNodeName(node.getNodeName());
        task.setApproveType(node.getApproveType());
        task.setApproverId(approverId);
        task.setTaskStatus(0);
        task.setCreateTime(LocalDateTime.now());
        sysApprovalTaskMapper.insert(task);
    }

    private void skipOtherTodoTasks(Long instanceId, Integer nodeOrder, Long handledTaskId) {
        List<SysApprovalTaskDO> todoTasks = sysApprovalTaskMapper.selectList(
                Wrappers.lambdaQuery(SysApprovalTaskDO.class)
                        .eq(SysApprovalTaskDO::getInstanceId, instanceId)
                        .eq(SysApprovalTaskDO::getNodeOrder, nodeOrder)
                        .eq(SysApprovalTaskDO::getTaskStatus, 0)
                        .ne(SysApprovalTaskDO::getTaskId, handledTaskId));
        for (SysApprovalTaskDO todoTask : todoTasks) {
            todoTask.setTaskStatus(2);
            todoTask.setApproveResult(3);
            todoTask.setComment("或签节点已由其他审批人处理");
            todoTask.setHandleTime(LocalDateTime.now());
            sysApprovalTaskMapper.updateById(todoTask);
        }
    }

    private LoginUser applicantOf(SysApprovalInstanceDO instance) {
        LoginUser applicant = new LoginUser();
        applicant.setUserId(instance.getApplyUserId());
        applicant.setDeptId(instance.getApplyDeptId());
        return applicant;
    }

    private List<Long> resolveApprovers(SysApprovalNodeDO node, LoginUser applicant) {
        return switch (node.getApproverType()) {
            // ⚠️ 必须 distinct：节点配置里把同一个人填两次的话，这里会产出两个相同的 approverId，
            //    createTasks 就会给同一个人插两条待办。之后该节点审批时，audit 里那个没有 LIMIT 的
            //    selectOne 会撞上 TooManyResultsException —— 节点再也审不动。下面的角色分支
            //    (case 2) 一直有 distinct，指定人员这一支漏了。
            //    顺带滤掉 null：JSON 里混进 null 会插出一条 approver_id 为空的任务，谁都审不了。
            case 1 -> JSONUtil.parseArray(Objects.toString(node.getApproverValue(), "[]"))
                    .toList(Long.class).stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            case 2 -> {
                List<Long> roleIds = JSONUtil.parseArray(Objects.toString(node.getApproverValue(), "[]"))
                        .toList(Long.class);
                List<SysUserRoleMiniDO> links = roleIds.isEmpty() ? List.of()
                        : sysUserRoleMiniMapper.selectList(
                        Wrappers.lambdaQuery(SysUserRoleMiniDO.class).in(SysUserRoleMiniDO::getRoleId, roleIds));
                yield links.stream().map(SysUserRoleMiniDO::getUserId).distinct().toList();
            }
            case 3, 4 -> {
                SysUserMiniDO user = sysUserMiniMapper.selectById(applicant.getUserId());
                yield user != null && user.getSuperiorId() != null
                        ? List.of(user.getSuperiorId()) : List.of();
            }
            case 5 -> List.of(applicant.getUserId());
            default -> List.of();
        };
    }

    private Long currentInstanceId(Long contractId) {
        SysApprovalInstanceDO instance = sysApprovalInstanceMapper.selectOne(
                Wrappers.lambdaQuery(SysApprovalInstanceDO.class)
                        .eq(SysApprovalInstanceDO::getBizType, BIZ_TYPE_CONTRACT)
                        .eq(SysApprovalInstanceDO::getBizId, contractId)
                        .eq(SysApprovalInstanceDO::getStatus, INSTANCE_RUNNING)
                        .orderByDesc(SysApprovalInstanceDO::getStartTime)
                        .last("LIMIT 1"));
        if (instance == null) throw new ServiceException(ErrorCode.APPROVAL_INSTANCE_NOT_EXISTS);
        return instance.getInstanceId();
    }

    // ==================== 审批进度查询的内部方法 ====================
    //
    // 这一组只读 sys_approval_* 四张表（flow / node / instance / task），把「实例 + 任务」
    // 翻译成「一条流程 + 每步结果」。没有一处写操作，也就没有 CAS / 并发问题。
    //
    // ⚠️ 抽取方向：合同、发票、回款迟早共用这套东西，届时整体搬到 crm-module-bpm，
    //    本组方法与 ContractServiceImpl#approval 一起搬即可（它们不依赖合同特有的东西，
    //    只依赖 bizType/bizId 与当前登录人）。

    /** 当前启用的合同审批流模板；停用或未配置时返回 {@code null} */
    private SysApprovalFlowDO enabledContractFlow() {
        // flow_code 上有 uk_flow_code(flow_code, is_active)，未删除的行最多一条，selectOne 安全
        return sysApprovalFlowMapper.selectOne(
                Wrappers.lambdaQuery(SysApprovalFlowDO.class)
                        .eq(SysApprovalFlowDO::getFlowCode, FLOW_CODE_CONTRACT_AUDIT)
                        .eq(SysApprovalFlowDO::getStatus, 1));
    }

    /** 模板节点，按 node_order 升序 */
    private List<SysApprovalNodeDO> nodesOfFlow(Long flowId) {
        return sysApprovalNodeMapper.selectList(
                Wrappers.lambdaQuery(SysApprovalNodeDO.class)
                        .eq(SysApprovalNodeDO::getFlowId, flowId)
                        .orderByAsc(SysApprovalNodeDO::getNodeOrder));
    }

    /** 多个审批流的模板节点：flowId → 节点（按 node_order 升序）—— 列表页一次查一页用到的全部流程 */
    private Map<Long, List<SysApprovalNodeDO>> nodesByFlowIds(Collection<Long> flowIds) {
        if (flowIds == null || flowIds.isEmpty()) {
            return Map.of();
        }
        return sysApprovalNodeMapper.selectList(
                        Wrappers.lambdaQuery(SysApprovalNodeDO.class)
                                .in(SysApprovalNodeDO::getFlowId, flowIds)
                                .orderByAsc(SysApprovalNodeDO::getNodeOrder)).stream()
                .filter(node -> node.getFlowId() != null)
                .collect(Collectors.groupingBy(SysApprovalNodeDO::getFlowId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    /** 当前节点之后的第一个模板节点（按 node_order）；没有则为 null */
    private SysApprovalNodeDO nextNode(List<SysApprovalNodeDO> nodes, Integer currentNodeOrder) {
        if (nodes == null || currentNodeOrder == null) {
            return null;
        }
        return nodes.stream()
                .filter(node -> node.getNodeOrder() != null && node.getNodeOrder() > currentNodeOrder)
                .findFirst().orElse(null);
    }

    /**
     * 从「flowId → 模板节点」里取某个流程的节点。
     *
     * <p>⚠️ 不能直接 {@code nodesByFlowId.get(flowId)}：空集合是用 {@code Map.of()} 表示的，
     * 而它对 {@code null} 键会抛 NPE（脏数据里 flow_id 可能为空）——统一从这里取。
     */
    private List<SysApprovalNodeDO> nodesOf(Map<Long, List<SysApprovalNodeDO>> nodesByFlowId, Long flowId) {
        return flowId == null ? null : nodesByFlowId.get(flowId);
    }

    /**
     * 批量预解析「尚未到达的节点」的**预计审批人** —— 列表页与详情页共用这一份。
     *
     * <p>只处理 {@code nodeOrder > currentNodeOrder} 的节点：正常流程下这些节点还没有建过任务，
     * 所以这里**不查任务表** —— 这是待审核列表能保持「每页固定几次查询、不随行数增长」的关键。
     *
     * <p>⚠️ <b>规则必须与 {@link #resolveApprovers} 一一对应</b>：真实审批人由它决定。
     * 两边一旦漂移，界面就会指着一个「永远收不到待办」的人。
     * {@code ContractServiceImplTest} 里有一条对照用例把这条钉住。
     *
     * <p>顺序不是契约（这里按 id 升序，便于结果可重现）；查不到姓名的会被略过。
     *
     * @param instances    进行中的审批实例（列表页一次传一页里的全部）
     * @param nodesByFlowId flowId → 该流程的模板节点
     * @return instanceId → (nodeOrder → 预计审批人姓名)
     */
    private Map<Long, Map<Integer, List<String>>> predictApprovers(
            Collection<SysApprovalInstanceDO> instances,
            Map<Long, List<SysApprovalNodeDO>> nodesByFlowId) {
        Map<Long, Map<Integer, List<String>>> result = new LinkedHashMap<>();
        if (instances == null || instances.isEmpty() || nodesByFlowId.isEmpty()) {
            return result;
        }

        // 1) 摊平「每个实例要预测哪些节点」，同时收集批量查询需要的值
        Map<Long, SysApprovalInstanceDO> instanceById = new LinkedHashMap<>();
        Map<Long, List<SysApprovalNodeDO>> pendingByInstance = new LinkedHashMap<>();
        Set<Long> roleIds = new HashSet<>();
        Set<Long> applicantIds = new HashSet<>();
        for (SysApprovalInstanceDO instance : instances) {
            if (instance == null || instance.getInstanceId() == null) {
                continue;
            }
            List<SysApprovalNodeDO> nodes = nodesOf(nodesByFlowId, instance.getFlowId());
            if (nodes == null) {
                continue;
            }
            List<SysApprovalNodeDO> futureNodes = nodes.stream()
                    .filter(node -> node.getNodeOrder() != null && instance.getCurrentNodeOrder() != null
                            && node.getNodeOrder() > instance.getCurrentNodeOrder())
                    .toList();
            if (futureNodes.isEmpty()) {
                continue;
            }
            instanceById.put(instance.getInstanceId(), instance);
            pendingByInstance.put(instance.getInstanceId(), futureNodes);
            for (SysApprovalNodeDO node : futureNodes) {
                Integer approverType = node.getApproverType();
                if (Integer.valueOf(APPROVER_ROLE).equals(approverType)) {
                    roleIds.addAll(parseIds(node.getApproverValue()));
                } else if (Integer.valueOf(APPROVER_DEPT_LEADER).equals(approverType)
                        || Integer.valueOf(APPROVER_SUPERIOR).equals(approverType)) {
                    if (instance.getApplyUserId() != null) {
                        applicantIds.add(instance.getApplyUserId());
                    }
                }
            }
        }
        if (pendingByInstance.isEmpty()) {
            return result;
        }

        // 2) 两次批量查询解决两类「需要查库」的审批人类型
        Map<Long, List<Long>> userIdsByRole = roleIds.isEmpty() ? Map.of()
                : sysUserRoleMiniMapper.selectList(
                        Wrappers.lambdaQuery(SysUserRoleMiniDO.class)
                                .in(SysUserRoleMiniDO::getRoleId, roleIds)).stream()
                .filter(link -> link.getRoleId() != null && link.getUserId() != null)
                .collect(Collectors.groupingBy(SysUserRoleMiniDO::getRoleId,
                        Collectors.mapping(SysUserRoleMiniDO::getUserId, Collectors.toList())));
        Map<Long, Long> superiorByUser = applicantIds.isEmpty() ? Map.of()
                : sysUserMiniMapper.selectList(SysUserMiniDO::getUserId, applicantIds).stream()
                .filter(user -> user.getUserId() != null && user.getSuperiorId() != null)
                .collect(Collectors.toMap(SysUserMiniDO::getUserId, SysUserMiniDO::getSuperiorId, (a, b) -> a));

        // 3) 逐实例逐节点算 id（与 resolveApprovers 同规则），最后一次批量补姓名
        Map<Long, Map<Integer, List<Long>>> idsByInstance = new LinkedHashMap<>();
        Set<Long> allApproverIds = new HashSet<>();
        for (Map.Entry<Long, List<SysApprovalNodeDO>> entry : pendingByInstance.entrySet()) {
            Long applyUserId = instanceById.get(entry.getKey()).getApplyUserId();
            Map<Integer, List<Long>> idsByNode = new LinkedHashMap<>();
            for (SysApprovalNodeDO node : entry.getValue()) {
                List<Long> ids = predictedApproverIds(node, applyUserId, userIdsByRole, superiorByUser);
                idsByNode.put(node.getNodeOrder(), ids);
                allApproverIds.addAll(ids);
            }
            idsByInstance.put(entry.getKey(), idsByNode);
        }
        Map<Long, String> names = userNames(allApproverIds);
        idsByInstance.forEach((instanceId, idsByNode) -> {
            Map<Integer, List<String>> namesByNode = new LinkedHashMap<>();
            idsByNode.forEach((nodeOrder, ids) -> namesByNode.put(nodeOrder,
                    ids.stream().map(names::get).filter(Objects::nonNull).toList()));
            result.put(instanceId, namesByNode);
        });
        return result;
    }

    /**
     * 单个节点的预计审批人 id —— 与 {@link #resolveApprovers} 的分支逐条对应。
     *
     * <p>⚠️ 3「部门主管」与 4「发起人直属上级」这里都按**直属上级**解析：现有
     * {@code resolveApprovers} 就是这么实现的（它把 case 3,4 合并了）。预测必须跟它一致，
     * 否则界面显示的人与真正收到待办的人不是同一个 —— 这是沿用的行为，不是本功能引入的偏差。
     */
    private List<Long> predictedApproverIds(SysApprovalNodeDO node, Long applyUserId,
                                            Map<Long, List<Long>> userIdsByRole,
                                            Map<Long, Long> superiorByUser) {
        int approverType = node.getApproverType() == null ? 0 : node.getApproverType();
        List<Long> ids = switch (approverType) {
            case APPROVER_USER -> parseIds(node.getApproverValue());
            case APPROVER_ROLE -> parseIds(node.getApproverValue()).stream()
                    .flatMap(roleId -> userIdsByRole.getOrDefault(roleId, List.of()).stream())
                    .toList();
            case APPROVER_DEPT_LEADER, APPROVER_SUPERIOR -> {
                Long superiorId = applyUserId == null ? null : superiorByUser.get(applyUserId);
                yield superiorId == null ? List.of() : List.of(superiorId);
            }
            case APPROVER_SELF -> applyUserId == null ? List.of() : List.of(applyUserId);
            default -> List.of();
        };
        return ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }

    /** {@code approver_value} 是 JSON 数组（如 {@code "[4]"}）；脏值当作空数组，不让它把接口打挂 */
    private List<Long> parseIds(String approverValue) {
        return JSONUtil.parseArray(Objects.toString(approverValue, "[]")).toList(Long.class).stream()
                .filter(Objects::nonNull)
                .toList();
    }

    /** 取某实例的「节点号 → 预计审批人姓名」，没有预测结果时返回空 Map */
    private Map<Integer, List<String>> predictedOf(Map<Long, Map<Integer, List<String>>> predicted,
                                                   Long instanceId) {
        Map<Integer, List<String>> byNode = instanceId == null ? null : predicted.get(instanceId);
        return byNode == null ? Map.of() : byNode;
    }

    /** 取某节点的预计审批人姓名；该节点没有预测结果时返回空列表 */
    private List<String> predictedNames(Map<Integer, List<String>> predictedByNode, Integer nodeOrder) {
        if (predictedByNode == null || nodeOrder == null) {
            return List.of();
        }
        return predictedByNode.getOrDefault(nodeOrder, List.of());
    }

    /**
     * 节点进度列表。
     *
     * <p>以**模板节点**为主干；模板上已经不存在的节点号（配置被改过、而实例正停在那一步）
     * 用任务上的 {@code node_name} 快照补出来 —— 否则会出现「当前节点：财务审批」但列表里
     * 根本没有「财务审批」这一步的错乱。
     */
    private List<ContractApprovalNodeVO> approvalNodes(List<SysApprovalNodeDO> templateNodes,
                                                       Map<Integer, List<SysApprovalTaskDO>> tasksByNode,
                                                       Map<Long, String> names,
                                                       Map<Integer, List<String>> predictedByNode) {
        List<ContractApprovalNodeVO> result = new ArrayList<>();
        Set<Integer> covered = new HashSet<>();
        for (SysApprovalNodeDO node : templateNodes) {
            Integer order = node.getNodeOrder();
            covered.add(order);
            ContractApprovalNodeVO vo = new ContractApprovalNodeVO();
            vo.setNodeId(node.getNodeId());
            vo.setNodeOrder(order);
            vo.setNodeName(node.getNodeName());
            vo.setApproveType(DictLabels.approveType(node.getApproveType()));
            vo.setApproverType(DictLabels.approverType(node.getApproverType()));
            fillNodeProgress(vo, tasksByNode.get(order), names);
            vo.setPredictedApproverNames(predictedNames(predictedByNode, order));
            result.add(vo);
        }
        for (Map.Entry<Integer, List<SysApprovalTaskDO>> entry : tasksByNode.entrySet()) {
            if (covered.contains(entry.getKey())) {
                continue;
            }
            ContractApprovalNodeVO vo = new ContractApprovalNodeVO();
            vo.setNodeOrder(entry.getKey());
            vo.setNodeName(taskNodeName(entry.getValue())); // 只有快照名可用
            fillNodeProgress(vo, entry.getValue(), names);
            result.add(vo);
        }
        result.sort(Comparator.comparing(ContractApprovalNodeVO::getNodeOrder,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    /** 一个节点的进度、待办人与任务明细 */
    private void fillNodeProgress(ContractApprovalNodeVO vo, List<SysApprovalTaskDO> nodeTasks,
                                  Map<Long, String> names) {
        vo.setProgress(nodeProgress(nodeTasks).getLabel());
        vo.setPendingApproverNames(pendingApproverNames(nodeTasks, names));
        vo.setTasks(toTaskVOs(nodeTasks, names));
    }

    /**
     * 把某节点的全部任务归并成一句话（规则见 {@link ApprovalNodeProgress}）。
     *
     * <p>顺序即优先级：驳回 > 待审批 > 通过 > 已跳过。会签节点「过了一半」必须落在
     * 「待审批」，否则前端会把没走完的节点画成已完成。
     */
    private ApprovalNodeProgress nodeProgress(List<SysApprovalTaskDO> nodeTasks) {
        if (nodeTasks == null || nodeTasks.isEmpty()) {
            return ApprovalNodeProgress.NOT_STARTED;
        }
        if (nodeTasks.stream().anyMatch(t -> Integer.valueOf(RESULT_REJECTED).equals(t.getApproveResult()))) {
            return ApprovalNodeProgress.REJECTED;
        }
        if (nodeTasks.stream().anyMatch(t -> Integer.valueOf(TASK_PENDING).equals(t.getTaskStatus()))) {
            return ApprovalNodeProgress.PENDING;
        }
        if (nodeTasks.stream().anyMatch(t -> Integer.valueOf(RESULT_APPROVED).equals(t.getApproveResult()))) {
            return ApprovalNodeProgress.APPROVED;
        }
        return ApprovalNodeProgress.SKIPPED;
    }

    /** 某节点「还在等谁」。姓名查不到时跳过，权威身份看同节点的 {@code tasks[].approverId} */
    private List<String> pendingApproverNames(List<SysApprovalTaskDO> nodeTasks, Map<Long, String> names) {
        if (nodeTasks == null || nodeTasks.isEmpty()) {
            return List.of();
        }
        return nodeTasks.stream()
                .filter(t -> Integer.valueOf(TASK_PENDING).equals(t.getTaskStatus()))
                .map(t -> names.get(t.getApproverId()))
                .filter(Objects::nonNull)
                .toList();
    }

    /** 任务明细，按生成时间升序（同一节点内即「谁先被派到」的顺序） */
    private List<ContractApprovalTaskVO> toTaskVOs(List<SysApprovalTaskDO> nodeTasks, Map<Long, String> names) {
        if (nodeTasks == null || nodeTasks.isEmpty()) {
            return List.of();
        }
        return nodeTasks.stream()
                .sorted(Comparator.comparing(SysApprovalTaskDO::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(task -> {
                    ContractApprovalTaskVO vo = new ContractApprovalTaskVO();
                    vo.setTaskId(task.getTaskId());
                    vo.setApproverId(task.getApproverId());
                    vo.setApproverName(names.get(task.getApproverId()));
                    vo.setTaskStatus(DictLabels.approvalTaskStatus(task.getTaskStatus()));
                    vo.setApproveResult(DictLabels.approveResult(task.getApproveResult()));
                    vo.setComment(task.getComment());
                    vo.setHandleTime(task.getHandleTime());
                    vo.setCreateTime(task.getCreateTime());
                    return vo;
                })
                .toList();
    }

    /** 任务上冗余的节点名快照（模板节点被删或查不到时的兜底） */
    private String taskNodeName(List<SysApprovalTaskDO> nodeTasks) {
        if (nodeTasks == null) {
            return null;
        }
        return nodeTasks.stream()
                .map(SysApprovalTaskDO::getNodeName)
                .filter(StringUtils::hasText)
                .findFirst().orElse(null);
    }

    /** 参与姓名补值的全部用户：所有任务的审批人 + 发起人 */
    private List<Long> taskApproverIds(List<SysApprovalTaskDO> tasks, SysApprovalInstanceDO instance) {
        List<Long> ids = new ArrayList<>(tasks.stream().map(SysApprovalTaskDO::getApproverId).toList());
        if (instance != null) {
            ids.add(instance.getApplyUserId());
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    /**
     * 数据异常提示 —— 这一条正是「审核中却审不动」的自我说明。正常情况返回 {@code null}。
     *
     * <p>三个分支都只描述**看得到的事实**，不做任何自愈写库：静默改状态比报错危险得多。
     */
    private String abnormalHint(ContractDO contract, SysApprovalInstanceDO instance) {
        boolean auditing = Integer.valueOf(AUDIT_AUDITING).equals(contract.getAuditStatus());
        if (instance == null) {
            return auditing
                    ? "该合同标记为「审核中」，但没有审批实例（历史 / 演示数据遗留）："
                    + "任何审批动作都会返回 4003「审批实例不存在」，需要重新提交审核。"
                    : null;
        }
        if (auditing && !Integer.valueOf(INSTANCE_RUNNING).equals(instance.getStatus())) {
            return "审批实例已结束（" + DictLabels.approvalInstanceStatus(instance.getStatus())
                    + "），但合同仍标记为「审核中」，两者不一致。";
        }
        if (!auditing && Integer.valueOf(INSTANCE_RUNNING).equals(instance.getStatus())) {
            return "存在进行中的审批实例，但合同审核态是「"
                    + Objects.toString(ContractAuditStatus.labelOf(contract.getAuditStatus()), "未知")
                    + "」，两者不一致。";
        }
        return null;
    }

    /**
     * 给待审核列表回填「当前节点 + 还在等谁」。
     *
     * <p>只用三次查询（进行中实例 → 这些实例的待办任务 → 姓名），不逐个合同查库；
     * 节点名直接取任务上的 {@code node_name} 快照，不再查节点表。
     */
    private void fillAuditProgress(List<ContractRespVO> list, List<Long> contractIds) {
        if (list.isEmpty() || contractIds.isEmpty()) {
            return;
        }
        List<SysApprovalInstanceDO> instances = sysApprovalInstanceMapper.selectList(
                Wrappers.lambdaQuery(SysApprovalInstanceDO.class)
                        .eq(SysApprovalInstanceDO::getBizType, BIZ_TYPE_CONTRACT)
                        .in(SysApprovalInstanceDO::getBizId, contractIds)
                        .eq(SysApprovalInstanceDO::getStatus, INSTANCE_RUNNING));
        if (instances.isEmpty()) {
            return;
        }
        Map<Long, SysApprovalInstanceDO> instanceByContract = instances.stream()
                .collect(Collectors.toMap(SysApprovalInstanceDO::getBizId, Function.identity(), (a, b) -> a));
        List<SysApprovalTaskDO> pendingTasks = sysApprovalTaskMapper.selectList(
                Wrappers.lambdaQuery(SysApprovalTaskDO.class)
                        .in(SysApprovalTaskDO::getInstanceId,
                                instances.stream().map(SysApprovalInstanceDO::getInstanceId).toList())
                        .eq(SysApprovalTaskDO::getTaskStatus, TASK_PENDING));
        // 姓名一次补齐：待办审批人 + 各自的提交人（applyUserId）
        List<Long> nameIds = new ArrayList<>(pendingTasks.stream().map(SysApprovalTaskDO::getApproverId).toList());
        instances.forEach(instance -> nameIds.add(instance.getApplyUserId()));
        Map<Long, String> names = userNames(nameIds);
        // 实例ID → 当前节点号：只有「任务节点号 == 实例当前节点号」才算挂着我的待办
        Map<Long, Integer> currentNodeByInstance = instances.stream()
                .collect(Collectors.toMap(SysApprovalInstanceDO::getInstanceId,
                        SysApprovalInstanceDO::getCurrentNodeOrder, (a, b) -> a));
        Map<Long, List<SysApprovalTaskDO>> currentTasksByInstance = pendingTasks.stream()
                .filter(task -> Objects.equals(task.getNodeOrder(),
                        currentNodeByInstance.get(task.getInstanceId())))
                .collect(Collectors.groupingBy(SysApprovalTaskDO::getInstanceId));
        // 「下一步到谁」：模板节点按 flowId 汇总成一次查询，再交给 predictApprovers 批量解析
        Map<Long, List<SysApprovalNodeDO>> nodesByFlowId = nodesByFlowIds(
                instances.stream().map(SysApprovalInstanceDO::getFlowId)
                        .filter(Objects::nonNull).distinct().toList());
        Map<Long, Map<Integer, List<String>>> predicted =
                predictApprovers(instances, nodesByFlowId);
        for (ContractRespVO vo : list) {
            SysApprovalInstanceDO instance = instanceByContract.get(vo.getContractId());
            if (instance == null) {
                continue;
            }
            // 提交人 / 提交时间只有审批实例上才有（列表里的 creator/createTime 是建单人、建单时间）
            vo.setApplyUserName(names.get(instance.getApplyUserId()));
            vo.setApplyTime(instance.getStartTime());
            List<SysApprovalTaskDO> currentTasks = currentTasksByInstance.get(instance.getInstanceId());
            if (currentTasks != null && !currentTasks.isEmpty()) {
                vo.setCurrentNodeName(taskNodeName(currentTasks));
                vo.setPendingApproverNames(pendingApproverNames(currentTasks, names));
            }
            SysApprovalNodeDO nextNode = nextNode(nodesOf(nodesByFlowId, instance.getFlowId()),
                    instance.getCurrentNodeOrder());
            if (nextNode != null) {
                vo.setNextNodeName(nextNode.getNodeName());
                vo.setNextApproverNames(predictedNames(
                        predictedOf(predicted, instance.getInstanceId()), nextNode.getNodeOrder()));
            }
        }
    }

    private void editable(ContractDO contract) {
        if (!Integer.valueOf(0).equals(contract.getStatus())
                || !(Integer.valueOf(0).equals(contract.getAuditStatus())
                || Integer.valueOf(3).equals(contract.getAuditStatus()))) {
            throw new ServiceException(ErrorCode.CONTRACT_AUDIT_STATUS_ERROR, "审核中、已通过或已生效合同不允许编辑或删除");
        }
    }

    private ContractDO getContract(Long id) {
        ContractDO contract = contractMapper.selectById(id);
        if (contract == null) throw new ServiceException(ErrorCode.CONTRACT_NOT_EXISTS);
        return contract;
    }

    private void checkCustomer(Long id) {
        CustomerDO customer = customerMapper.selectById(id);
        if (customer == null) throw new ServiceException(ErrorCode.CUSTOMER_NOT_EXISTS);
    }

    private List<ContractItemDO> buildItems(List<ContractItemReqVO> requests, Long contractId) {
        return requests.stream().map(req -> {
            BigDecimal quantity = req.getQuantity();
            BigDecimal unitPrice = req.getUnitPrice();
            BigDecimal discount = value(req.getDiscountRate());
            BigDecimal tax = value(req.getTaxRate());
            if (quantity == null || quantity.signum() <= 0 || unitPrice == null || unitPrice.signum() < 0
                    || discount.signum() < 0 || discount.compareTo(BigDecimal.valueOf(100)) > 0
                    || tax.signum() < 0 || tax.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "合同明细金额、数量、折扣率或税率不合法");
            }
            ContractItemDO item = contractConvert.toItemDO(req);
            item.setItemId(IdUtil.getSnowflakeNextId());
            item.setContractId(contractId);
            item.setDiscountRate(discount);
            item.setTaxRate(tax);
            item.setAmount(quantity.multiply(unitPrice)
                    .multiply(BigDecimal.ONE.subtract(discount.movePointLeft(2)))
                    .setScale(2, RoundingMode.HALF_UP));
            return item;
        }).toList();
    }

    private BigDecimal sum(List<ContractItemDO> items) {
        return items.stream().map(ContractItemDO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String ownerName(Long id) {
        return userName(id);
    }

    // ==================== 内部方法：跨表补值 ====================
    //
    // 合同 VO 里 customerName / opportunityName / quotationNo / creator 都不是本表字段，
    // 全部在这里批量补齐 —— 逐行查库会让列表页发 N 次 SQL，与商机模块的做法保持一致。

    /** 批量查用户姓名（负责人与创建人共用） */
    private Map<Long, String> userNames(Collection<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            // ⚠️ 必须用 Collections.emptyMap() 而不是 Map.of()：后者是
            //    ImmutableCollections.MapN，size == 0 时 get() 会先 Objects.requireNonNull(key)，
            //    而调用处可能传 null（创建人为空时），会抛 NPE 让整个列表接口 500。
            return Collections.emptyMap();
        }
        return sysUserMiniMapper.selectList(SysUserMiniDO::getUserId, ids).stream()
                .filter(u -> u.getRealName() != null)
                .collect(Collectors.toMap(SysUserMiniDO::getUserId, SysUserMiniDO::getRealName, (a, b) -> a));
    }

    /** 查单个用户姓名 */
    private String userName(Long id) {
        if (id == null) {
            return null;
        }
        SysUserMiniDO user = sysUserMiniMapper.selectById(id);
        return user == null ? null : user.getRealName();
    }

    /** 批量查客户名 */
    private Map<Long, String> customerNames(List<ContractDO> records) {
        List<Long> ids = records.stream().map(ContractDO::getCustomerId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return customerMiniMapper.selectList(CustomerMiniDO::getCustomerId, ids).stream()
                .filter(c -> c.getCustomerName() != null)
                .collect(Collectors.toMap(CustomerMiniDO::getCustomerId, CustomerMiniDO::getCustomerName, (a, b) -> a));
    }

    /** 批量查商机名 */
    private Map<Long, String> opportunityNames(List<ContractDO> records) {
        List<Long> ids = records.stream().map(ContractDO::getOpportunityId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return opportunityMapper.selectList(OpportunityDO::getOpportunityId, ids).stream()
                .filter(o -> o.getOpportunityName() != null)
                .collect(Collectors.toMap(OpportunityDO::getOpportunityId, OpportunityDO::getOpportunityName, (a, b) -> a));
    }

    /** 批量查报价单编号 */
    private Map<Long, String> quotationNos(List<ContractDO> records) {
        List<Long> ids = records.stream().map(ContractDO::getQuotationId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return quotationMapper.selectList(QuotationDO::getQuotationId, ids).stream()
                .filter(q -> q.getQuotationNo() != null)
                .collect(Collectors.toMap(QuotationDO::getQuotationId, QuotationDO::getQuotationNo, (a, b) -> a));
    }

    /** 单条查询时的客户名 */
    private String customerName(Long customerId) {
        if (customerId == null) {
            return null;
        }
        CustomerMiniDO customer = customerMiniMapper.selectById(customerId);
        return customer == null ? null : customer.getCustomerName();
    }

    /** 单条查询时的商机名 */
    private String opportunityName(Long opportunityId) {
        if (opportunityId == null) {
            return null;
        }
        OpportunityDO opportunity = opportunityMapper.selectById(opportunityId);
        return opportunity == null ? null : opportunity.getOpportunityName();
    }

    /** 单条查询时的报价单编号 */
    private String quotationNo(Long quotationId) {
        if (quotationId == null) {
            return null;
        }
        QuotationDO quotation = quotationMapper.selectById(quotationId);
        return quotation == null ? null : quotation.getQuotationNo();
    }
}
