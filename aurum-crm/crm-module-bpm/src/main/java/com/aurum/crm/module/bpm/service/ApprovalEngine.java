package com.aurum.crm.module.bpm.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.bpm.mapper.ApprovalFlowMapper;
import com.aurum.crm.module.bpm.mapper.ApprovalInstanceMapper;
import com.aurum.crm.module.bpm.mapper.ApprovalNodeMapper;
import com.aurum.crm.module.bpm.mapper.ApprovalTaskMapper;
import com.aurum.crm.module.bpm.mapper.BpmUserMapper;
import com.aurum.crm.module.bpm.mapper.BpmUserRoleMapper;
import com.aurum.crm.module.bpm.model.BpmUserDO;
import com.aurum.crm.module.bpm.model.BpmUserRoleDO;
import com.aurum.crm.module.bpm.model.SysApprovalFlowDO;
import com.aurum.crm.module.bpm.model.SysApprovalInstanceDO;
import com.aurum.crm.module.bpm.model.SysApprovalNodeDO;
import com.aurum.crm.module.bpm.model.SysApprovalTaskDO;
import com.aurum.crm.module.bpm.model.vo.ApprovalInstanceRespVO;
import com.aurum.crm.module.bpm.model.vo.ApprovalTaskRespVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 通用审批流引擎（bpm 模块）。
 *
 * <p>与 {@code crm-module-crm} 里合同审批的引擎逻辑同构 —— 本类是把「模板 → 实例 → 任务」
 * 的推进规则抽成 <b>与具体业务对象无关</b> 的引擎，按 {@code flowCode} / {@code bizType} /
 * {@code bizId} 驱动，供合同、发票、回款等业务复用。
 *
 * <p><b>边界</b>：本引擎只管 {@code sys_approval_flow/node/instance/task} 四张表。
 * 业务对象自身状态的落库（如合同审核态、发票状态）由<b>调用方</b>根据本引擎的
 * {@link ApprovalResult} 去写 —— 引擎不反向依赖任何业务 DO。并发一致性的核心仍是
 * {@link #audit} 里认领任务的那次原子 CAS（{@code task_status → 1}）。
 */
@Service
public class ApprovalEngine {

    /** 或签：任一人通过即代表节点通过 */
    private static final int APPROVE_TYPE_OR = 1;
    /** 会签：节点内所有人通过才算过 */
    private static final int APPROVE_TYPE_AND = 2;
    /** 依次审批：任务串行逐人 */
    private static final int APPROVE_TYPE_SEQ = 3;

    /** 审批人类型：指定人员 / 角色 / 部门主管 / 直属上级 / 发起人自己 */
    private static final int APPROVER_USER = 1;
    private static final int APPROVER_ROLE = 2;
    private static final int APPROVER_DEPT_LEADER = 3;
    private static final int APPROVER_SUPERIOR = 4;
    private static final int APPROVER_SELF = 5;

    private static final int TASK_PENDING = 0;
    private static final int TASK_HANDLED = 1;
    private static final int TASK_SKIPPED = 2;

    private static final int INSTANCE_RUNNING = 0;
    private static final int INSTANCE_APPROVED = 1;
    private static final int INSTANCE_REJECTED = 2;

    @Resource
    private ApprovalFlowMapper approvalFlowMapper;
    @Resource
    private ApprovalNodeMapper approvalNodeMapper;
    @Resource
    private ApprovalInstanceMapper approvalInstanceMapper;
    @Resource
    private ApprovalTaskMapper approvalTaskMapper;
    @Resource
    private BpmUserMapper bpmUserMapper;
    @Resource
    private BpmUserRoleMapper bpmUserRoleMapper;

    /**
     * 提交审批：为业务对象创建审批实例与首节点待办任务。
     *
     * <p>调用方必须先完成自身的「可提审」校验与原子状态抢占（如合同把 audit_status 置 1），
     * 再调用本方法建实例。若重复提审会创建两条实例，业务层需自行保证幂等。
     *
     * @param flowCode 审批流模板编码（如 {@code CONTRACT_AUDIT}）
     * @param bizType 业务类型（如 {@code contract}）
     * @param bizId   业务对象 ID
     * @param bizNo   业务编号（冗余展示）
     * @param title   标题（冗余展示）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(String flowCode, String bizType, Long bizId, String bizNo, String title) {
        SysApprovalFlowDO flow = approvalFlowMapper.selectOne(
                Wrappers.<SysApprovalFlowDO>lambdaQuery()
                        .eq(SysApprovalFlowDO::getFlowCode, flowCode)
                        .eq(SysApprovalFlowDO::getStatus, 1));
        if (flow == null) {
            throw new ServiceException(ErrorCode.APPROVAL_FLOW_NOT_EXISTS);
        }
        List<SysApprovalNodeDO> nodes = nodesOfFlow(flow.getFlowId());
        if (nodes.isEmpty()) {
            throw new ServiceException(ErrorCode.APPROVAL_FLOW_NOT_EXISTS, "审批流没有配置节点");
        }
        LoginUser user = LoginUserHolder.getRequired();
        SysApprovalInstanceDO instance = new SysApprovalInstanceDO();
        instance.setInstanceId(IdUtil.getSnowflakeNextId());
        instance.setFlowId(flow.getFlowId());
        instance.setBizType(bizType);
        instance.setBizId(bizId);
        instance.setBizNo(bizNo);
        instance.setTitle(title);
        instance.setApplyUserId(user.getUserId());
        instance.setApplyDeptId(user.getDeptId());
        instance.setCurrentNodeOrder(nodes.get(0).getNodeOrder());
        instance.setStatus(INSTANCE_RUNNING);
        instance.setStartTime(LocalDateTime.now());
        approvalInstanceMapper.insert(instance);
        createTasks(instance, nodes.get(0), user);
    }

    /**
     * 审批一个待办任务，推进到下一节点；最后一节点通过则实例通过，任一驳回则实例驳回。
     *
     * <p>入口约定：调用方负责校验业务对象当前在审批中、并定位当前用户的任务。这里以
     * {@code (bizType, bizId, approverId, 待办)} 复合条件做一次原子 CAS 认领，避免重复处理。
     *
     * @param bizType 业务类型
     * @param bizId   业务对象 ID
     * @param result  审批结果：1 通过 / 2 驳回
     * @param comment 审批意见
     * @return 本次处理对实例的推进结果（用于调用方决定业务对象状态该怎么落）
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalResult audit(String bizType, Long bizId, Integer result, String comment) {
        if (result == null || (result != 1 && result != 2)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "审批结果必须为 1 通过或 2 驳回");
        }
        Long userId = LoginUserHolder.getRequired().getUserId();
        Long instanceId = runningInstanceId(bizType, bizId);
        SysApprovalTaskDO task = approvalTaskMapper.selectOne(
                Wrappers.<SysApprovalTaskDO>lambdaQuery()
                        .eq(SysApprovalTaskDO::getInstanceId, instanceId)
                        .eq(SysApprovalTaskDO::getApproverId, userId)
                        .eq(SysApprovalTaskDO::getTaskStatus, TASK_PENDING));
        if (task == null) {
            throw new ServiceException(ErrorCode.APPROVAL_NOT_YOUR_TASK);
        }
        // 原子 CAS：待办 → 已处理，防止同一审批人双击「通过」重复推进
        int claimed = approvalTaskMapper.update(null,
                Wrappers.<SysApprovalTaskDO>lambdaUpdate()
                        .eq(SysApprovalTaskDO::getTaskId, task.getTaskId())
                        .eq(SysApprovalTaskDO::getTaskStatus, TASK_PENDING)
                        .set(SysApprovalTaskDO::getTaskStatus, TASK_HANDLED)
                        .set(SysApprovalTaskDO::getApproveResult, result)
                        .set(SysApprovalTaskDO::getComment, comment)
                        .set(SysApprovalTaskDO::getHandleTime, LocalDateTime.now()));
        if (claimed == 0) {
            throw new ServiceException(ErrorCode.APPROVAL_TASK_HANDLED);
        }
        task.setTaskStatus(TASK_HANDLED);
        task.setApproveResult(result);
        task.setComment(comment);
        task.setHandleTime(LocalDateTime.now());

        SysApprovalInstanceDO instance = approvalInstanceMapper.selectById(instanceId);
        // 驳回：实例直接终止
        if (Integer.valueOf(2).equals(result)) {
            instance.setStatus(INSTANCE_REJECTED);
            instance.setEndTime(LocalDateTime.now());
            approvalInstanceMapper.updateById(instance);
            return ApprovalResult.rejected(instance);
        }
        // 或签：一人通过，其余同节点待办跳过
        if (Integer.valueOf(APPROVE_TYPE_OR).equals(task.getApproveType())) {
            skipOtherTodoTasks(instanceId, instance.getCurrentNodeOrder(), task.getTaskId());
        }
        // 依次审批：本节点还有待办的下一人
        if (Integer.valueOf(APPROVE_TYPE_SEQ).equals(task.getApproveType())) {
            SysApprovalNodeDO currentNode = approvalNodeMapper.selectOne(
                    Wrappers.<SysApprovalNodeDO>lambdaQuery()
                            .eq(SysApprovalNodeDO::getFlowId, instance.getFlowId())
                            .eq(SysApprovalNodeDO::getNodeOrder, instance.getCurrentNodeOrder()));
            if (currentNode != null) {
                List<Long> handled = approvalTaskMapper.selectList(
                                Wrappers.<SysApprovalTaskDO>lambdaQuery()
                                        .eq(SysApprovalTaskDO::getInstanceId, instanceId)
                                        .eq(SysApprovalTaskDO::getNodeOrder, instance.getCurrentNodeOrder()))
                        .stream().map(SysApprovalTaskDO::getApproverId).toList();
                LoginUser applicant = applicantOf(instance);
                Long next = resolveApprovers(currentNode, applicant).stream()
                        .filter(id -> !handled.contains(id))
                        .findFirst().orElse(null);
                if (next != null) {
                    createTask(instance, currentNode, next);
                    return ApprovalResult.running(instance);
                }
            }
        }
        // 会签 / 本节点全部待办已清：节点还有未处理任务就仍等待
        List<SysApprovalTaskDO> currentTasks = approvalTaskMapper.selectList(
                Wrappers.<SysApprovalTaskDO>lambdaQuery()
                        .eq(SysApprovalTaskDO::getInstanceId, instanceId)
                        .eq(SysApprovalTaskDO::getNodeOrder, instance.getCurrentNodeOrder()));
        if (currentTasks.stream().anyMatch(t -> Integer.valueOf(TASK_PENDING).equals(t.getTaskStatus()))) {
            return ApprovalResult.running(instance);
        }
        // 推进到下一节点，或实例通过
        List<SysApprovalNodeDO> nodes = nodesOfFlow(instance.getFlowId());
        SysApprovalNodeDO next = nodes.stream()
                .filter(n -> n.getNodeOrder() > instance.getCurrentNodeOrder())
                .findFirst().orElse(null);
        if (next == null) {
            instance.setStatus(INSTANCE_APPROVED);
            instance.setEndTime(LocalDateTime.now());
            approvalInstanceMapper.updateById(instance);
            return ApprovalResult.approved(instance);
        }
        instance.setCurrentNodeOrder(next.getNodeOrder());
        approvalInstanceMapper.updateById(instance);
        createTasks(instance, next, applicantOf(instance));
        return ApprovalResult.running(instance);
    }

    // ---------- 查询 ----------

    /** 某业务对象的最新审批实例（无则 {@code null}）。 */
    public SysApprovalInstanceDO latestInstance(String bizType, Long bizId) {
        return approvalInstanceMapper.selectOne(
                Wrappers.<SysApprovalInstanceDO>lambdaQuery()
                        .eq(SysApprovalInstanceDO::getBizType, bizType)
                        .eq(SysApprovalInstanceDO::getBizId, bizId)
                        .orderByDesc(SysApprovalInstanceDO::getStartTime)
                        .last("LIMIT 1"));
    }

    /** 某业务对象正在进行的审批实例（无则 {@code null}）。 */
    public SysApprovalInstanceDO runningInstance(String bizType, Long bizId) {
        return approvalInstanceMapper.selectOne(
                Wrappers.<SysApprovalInstanceDO>lambdaQuery()
                        .eq(SysApprovalInstanceDO::getBizType, bizType)
                        .eq(SysApprovalInstanceDO::getBizId, bizId)
                        .eq(SysApprovalInstanceDO::getStatus, INSTANCE_RUNNING)
                        .orderByDesc(SysApprovalInstanceDO::getStartTime)
                        .last("LIMIT 1"));
    }

    /** 实例的任务列表（按节点号、创建时间自然序）。 */
    public List<SysApprovalTaskDO> tasksOfInstance(Long instanceId) {
        return approvalTaskMapper.selectList(
                Wrappers.<SysApprovalTaskDO>lambdaQuery()
                        .eq(SysApprovalTaskDO::getInstanceId, instanceId));
    }

    /** 按任务 ID 取任务（无则 {@code null}）。 */
    public SysApprovalTaskDO taskById(Long taskId) {
        return approvalTaskMapper.selectById(taskId);
    }

    /**
     * 按任务 ID 审批（接口文档八节 {@code POST /bpm/task/{id}/approve}）。
     *
     * <p>先按 taskId 定位任务与其所属实例，再委托 {@link #audit} 走同一套原子 CAS 与推进逻辑。
     * 与 {@link #audit} 一样，驳回返回 {@code REJECTED}、最后节点通过返回 {@code APPROVED}。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalResult auditTask(Long taskId, Integer result, String comment) {
        SysApprovalTaskDO task = approvalTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException(ErrorCode.APPROVAL_TASK_NOT_EXISTS);
        }
        SysApprovalInstanceDO instance = approvalInstanceMapper.selectById(task.getInstanceId());
        if (instance == null) {
            throw new ServiceException(ErrorCode.APPROVAL_INSTANCE_NOT_EXISTS);
        }
        return audit(instance.getBizType(), instance.getBizId(), result, comment);
    }

    /** 当前登录人的全部待办任务。 */
    public List<SysApprovalTaskDO> myTodoTasks() {
        Long userId = LoginUserHolder.getRequired().getUserId();
        return approvalTaskMapper.selectList(
                Wrappers.<SysApprovalTaskDO>lambdaQuery()
                        .eq(SysApprovalTaskDO::getApproverId, userId)
                        .eq(SysApprovalTaskDO::getTaskStatus, TASK_PENDING));
    }

    /** 当前登录人的全部已办任务（task_status=1）。 */
    public List<SysApprovalTaskDO> myDoneTasks() {
        Long userId = LoginUserHolder.getRequired().getUserId();
        return approvalTaskMapper.selectList(
                Wrappers.<SysApprovalTaskDO>lambdaQuery()
                        .eq(SysApprovalTaskDO::getApproverId, userId)
                        .eq(SysApprovalTaskDO::getTaskStatus, TASK_HANDLED)
                        .orderByDesc(SysApprovalTaskDO::getHandleTime));
    }

    /** 当前登录人发起的审批实例。 */
    public List<SysApprovalInstanceDO> myInstances() {
        Long userId = LoginUserHolder.getRequired().getUserId();
        return approvalInstanceMapper.selectList(
                Wrappers.<SysApprovalInstanceDO>lambdaQuery()
                        .eq(SysApprovalInstanceDO::getApplyUserId, userId)
                        .orderByDesc(SysApprovalInstanceDO::getStartTime));
    }

    /** 待办/已办任务 → 展示 VO（补实例、单据号、标题、发起人姓名）。 */
    public List<ApprovalTaskRespVO> toTaskVOs(List<SysApprovalTaskDO> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<Long> instanceIds = tasks.stream().map(SysApprovalTaskDO::getInstanceId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, SysApprovalInstanceDO> instanceById = instanceIds.isEmpty() ? Map.of()
                : approvalInstanceMapper.selectList(
                        Wrappers.<SysApprovalInstanceDO>lambdaQuery()
                                .in(SysApprovalInstanceDO::getInstanceId, instanceIds))
                .stream().collect(java.util.stream.Collectors.toMap(
                        SysApprovalInstanceDO::getInstanceId, i -> i, (a, b) -> a));
        Map<Long, String> names = userNamesOfInstances(instanceById.values().stream().toList());
        return tasks.stream().map(t -> {
            SysApprovalInstanceDO inst = instanceById.get(t.getInstanceId());
            ApprovalTaskRespVO vo = new ApprovalTaskRespVO();
            vo.setTaskId(t.getTaskId());
            vo.setInstanceId(t.getInstanceId());
            vo.setNodeName(t.getNodeName());
            if (inst != null) {
                vo.setBizType(inst.getBizType());
                vo.setBizId(inst.getBizId());
                vo.setBizNo(inst.getBizNo());
                vo.setTitle(inst.getTitle());
                vo.setApplyUserName(names.get(inst.getApplyUserId()));
            }
            vo.setTaskStatus(t.getTaskStatus());
            vo.setApproveResult(t.getApproveResult());
            vo.setComment(t.getComment());
            vo.setHandleTime(t.getHandleTime());
            vo.setCreateTime(t.getCreateTime());
            return vo;
        }).toList();
    }

    /** 实例 → 展示 VO（补流程名、发起人姓名）。 */
    public List<ApprovalInstanceRespVO> toInstanceVOs(List<SysApprovalInstanceDO> instances) {
        if (instances == null || instances.isEmpty()) {
            return List.of();
        }
        List<Long> flowIds = instances.stream().map(SysApprovalInstanceDO::getFlowId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> flowNames = flowIds.isEmpty() ? Map.of()
                : approvalFlowMapper.selectList(
                                Wrappers.<SysApprovalFlowDO>lambdaQuery()
                                        .in(SysApprovalFlowDO::getFlowId, flowIds))
                .stream().collect(java.util.stream.Collectors.toMap(
                        SysApprovalFlowDO::getFlowId, SysApprovalFlowDO::getFlowName, (a, b) -> a));
        Map<Long, String> names = userNamesOfInstances(instances);
        return instances.stream().map(i -> {
            ApprovalInstanceRespVO vo = new ApprovalInstanceRespVO();
            vo.setInstanceId(i.getInstanceId());
            vo.setFlowId(i.getFlowId());
            vo.setFlowName(flowNames.get(i.getFlowId()));
            vo.setBizType(i.getBizType());
            vo.setBizId(i.getBizId());
            vo.setBizNo(i.getBizNo());
            vo.setTitle(i.getTitle());
            vo.setApplyUserId(i.getApplyUserId());
            vo.setApplyUserName(names.get(i.getApplyUserId()));
            vo.setCurrentNodeOrder(i.getCurrentNodeOrder());
            vo.setStatus(i.getStatus());
            vo.setStartTime(i.getStartTime());
            vo.setEndTime(i.getEndTime());
            return vo;
        }).toList();
    }

    private Map<Long, String> userNamesOfInstances(List<SysApprovalInstanceDO> instances) {
        List<Long> userIds = instances.stream().map(SysApprovalInstanceDO::getApplyUserId)
                .filter(Objects::nonNull).distinct().toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return bpmUserMapper.selectList(
                        Wrappers.<BpmUserDO>lambdaQuery().in(BpmUserDO::getUserId, userIds))
                .stream().collect(java.util.stream.Collectors.toMap(
                        BpmUserDO::getUserId, BpmUserDO::getRealName, (a, b) -> a));
    }

    // ---------- 内部 ----------

    private Long runningInstanceId(String bizType, Long bizId) {
        SysApprovalInstanceDO instance = runningInstance(bizType, bizId);
        if (instance == null) {
            throw new ServiceException(ErrorCode.APPROVAL_INSTANCE_NOT_EXISTS);
        }
        return instance.getInstanceId();
    }

    private List<SysApprovalNodeDO> nodesOfFlow(Long flowId) {
        return approvalNodeMapper.selectList(
                Wrappers.<SysApprovalNodeDO>lambdaQuery()
                        .eq(SysApprovalNodeDO::getFlowId, flowId)
                        .orderByAsc(SysApprovalNodeDO::getNodeOrder));
    }

    private void createTasks(SysApprovalInstanceDO instance, SysApprovalNodeDO node, LoginUser applicant) {
        List<Long> approverIds = resolveApprovers(node, applicant);
        if (approverIds.isEmpty()) {
            throw new ServiceException(ErrorCode.APPROVAL_FLOW_NOT_EXISTS,
                    "审批节点没有解析出审批人：" + node.getNodeName());
        }
        if (Integer.valueOf(APPROVE_TYPE_SEQ).equals(node.getApproveType())) {
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
        task.setTaskStatus(TASK_PENDING);
        task.setCreateTime(LocalDateTime.now());
        approvalTaskMapper.insert(task);
    }

    private void skipOtherTodoTasks(Long instanceId, Integer nodeOrder, Long handledTaskId) {
        List<SysApprovalTaskDO> todoTasks = approvalTaskMapper.selectList(
                Wrappers.<SysApprovalTaskDO>lambdaQuery()
                        .eq(SysApprovalTaskDO::getInstanceId, instanceId)
                        .eq(SysApprovalTaskDO::getNodeOrder, nodeOrder)
                        .eq(SysApprovalTaskDO::getTaskStatus, TASK_PENDING)
                        .ne(SysApprovalTaskDO::getTaskId, handledTaskId));
        for (SysApprovalTaskDO todo : todoTasks) {
            todo.setTaskStatus(TASK_SKIPPED);
            todo.setApproveResult(3);
            todo.setComment("或签节点已由其他审批人处理");
            todo.setHandleTime(LocalDateTime.now());
            approvalTaskMapper.updateById(todo);
        }
    }

    private LoginUser applicantOf(SysApprovalInstanceDO instance) {
        LoginUser applicant = new LoginUser();
        applicant.setUserId(instance.getApplyUserId());
        applicant.setDeptId(instance.getApplyDeptId());
        return applicant;
    }

    /** 解析节点审批人（与校验语义一致的类型分支）。 */
    private List<Long> resolveApprovers(SysApprovalNodeDO node, LoginUser applicant) {
        if (node.getApproverType() == null) {
            return List.of();
        }
        return switch (node.getApproverType()) {
            case APPROVER_USER -> parseIds(node.getApproverValue()).stream().distinct().toList();
            case APPROVER_ROLE -> {
                List<Long> roleIds = parseIds(node.getApproverValue());
                List<BpmUserRoleDO> links = roleIds.isEmpty() ? List.of()
                        : bpmUserRoleMapper.selectList(
                                Wrappers.<BpmUserRoleDO>lambdaQuery()
                                        .in(BpmUserRoleDO::getRoleId, roleIds));
                yield links.stream().map(BpmUserRoleDO::getUserId).distinct().toList();
            }
            case APPROVER_DEPT_LEADER, APPROVER_SUPERIOR -> {
                BpmUserDO user = applicant.getUserId() == null ? null
                        : bpmUserMapper.selectById(applicant.getUserId());
                yield user != null && user.getSuperiorId() != null
                        ? List.of(user.getSuperiorId()) : List.of();
            }
            case APPROVER_SELF -> applicant.getUserId() == null
                    ? List.of() : List.of(applicant.getUserId());
            default -> List.of();
        };
    }

    private List<Long> parseIds(String approverValue) {
        return JSONUtil.parseArray(Objects.toString(approverValue, "[]")).toList(Long.class).stream()
                .filter(Objects::nonNull)
                .toList();
    }
}
