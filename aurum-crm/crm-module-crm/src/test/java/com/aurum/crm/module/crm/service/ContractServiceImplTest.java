package com.aurum.crm.module.crm.service;

import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.mapper.ContractMapper;
import com.aurum.crm.module.crm.mapper.SysApprovalInstanceMapper;
import com.aurum.crm.module.crm.mapper.SysApprovalNodeMapper;
import com.aurum.crm.module.crm.mapper.SysApprovalTaskMapper;
import com.aurum.crm.module.crm.model.ContractDO;
import com.aurum.crm.module.crm.model.StarBizType;
import com.aurum.crm.module.crm.model.SysApprovalInstanceDO;
import com.aurum.crm.module.crm.model.SysApprovalTaskDO;
import com.aurum.crm.module.crm.model.dto.ContractAuditReqVO;
import com.aurum.crm.module.crm.model.dto.ContractPageReqVO;
import com.aurum.crm.module.crm.model.vo.ContractRespVO;
import com.aurum.crm.module.crm.service.StarService;
import com.aurum.crm.module.crm.service.impl.ContractServiceImpl;
import com.aurum.crm.mybatis.core.PageParam;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 合同审批的并发保护单测。
 *
 * <p>背景：{@code submit} / {@code audit} 原先都是「先读、再无条件写」。
 * {@code audit} 里的任务认领写成 {@code updateById(task)} —— 它按主键更新，WHERE 里只有
 * {@code task_id}，所以同一个审批人双击「通过」时两个事务都会成功。后果不是简单的重复处理：
 * 两个事务各自再读本节点待办时都只看到自己写进去的 {@code task_status=1}，于是都认为
 * 「本节点处理完了」，双双给下一节点插待办；之后那个节点审批时，{@code audit} 里没有 LIMIT 的
 * {@code selectOne} 撞上 {@code TooManyResultsException}，节点再也审不动。
 *
 * <p>这里覆盖的是修复后的行为：认领失败（CAS 影响 0 行）必须抛错并就此中断，
 * 绝不能再往下写审批实例。
 */
@ExtendWith(MockitoExtension.class)
class ContractServiceImplTest {

    @Mock
    private ContractMapper contractMapper;
    @Mock
    private SysApprovalTaskMapper sysApprovalTaskMapper;
    @Mock
    private SysApprovalInstanceMapper sysApprovalInstanceMapper;
    @Mock
    private SysApprovalNodeMapper sysApprovalNodeMapper;
    @Mock
    private StarService starService;

    @InjectMocks
    private ContractServiceImpl contractService;

    @BeforeEach
    void setUp() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(100L);
        loginUser.setDeptId(10L);
        LoginUserHolder.set(loginUser);
        // 纯 Mockito 单测没有 Spring 上下文，LambdaUpdateWrapper 解析列名需要
        // MP 的 TableInfo 缓存，这里手动初始化（幂等，重复 init 无害）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ContractDO.class);
        TableInfoHelper.initTableInfo(assistant, SysApprovalTaskDO.class);
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    /**
     * 任务已被别人处理：CAS 影响 0 行时必须抛 4005，且不能再改合同 / 审批实例。
     *
     * <p>这正是双击「通过」时后到那个请求走的分支。修复前它拿到的是
     * {@code updateById} 的「成功」，会一路往下把下一节点的待办插两遍。
     */
    @Test
    void audit_whenTaskAlreadyClaimed_throwsAndStopsBeforeAnyWrite() {
        ContractDO contract = new ContractDO();
        contract.setContractId(1L);
        contract.setAuditStatus(1);   // 审批中
        when(contractMapper.selectById(1L)).thenReturn(contract);

        // 注意：audit 里 currentInstanceId(id) 是在 sysApprovalTaskMapper.selectOne 的**参数**位置
        // 求值的，所以它内部的审批实例查询会先执行 —— 这个桩不能省，
        // 否则会先抛 APPROVAL_INSTANCE_NOT_EXISTS，测不到任务认领这一段。
        SysApprovalInstanceDO instance = new SysApprovalInstanceDO();
        instance.setInstanceId(8001L);
        when(sysApprovalInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(instance);

        SysApprovalTaskDO task = new SysApprovalTaskDO();
        task.setTaskId(9001L);
        task.setInstanceId(8001L);
        task.setApproverId(100L);
        task.setTaskStatus(0);
        when(sysApprovalTaskMapper.selectOne(any(Wrapper.class))).thenReturn(task);

        // 关键：CAS 影响 0 行 —— 表示这条任务已被并发请求抢先认领
        when(sysApprovalTaskMapper.update(any(), any(Wrapper.class))).thenReturn(0);

        ContractAuditReqVO req = new ContractAuditReqVO();
        req.setResult(1);

        assertThatThrownBy(() -> contractService.audit(1L, req))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(ErrorCode.APPROVAL_TASK_HANDLED.getCode());

        // 认领失败后不能再有任何写操作 —— 尤其是不能推进审批实例
        // （参数类型要写明：BaseMapper 同时有 updateById(T) 和 updateById(Collection<T>)，裸 any() 会歧义）
        verify(sysApprovalInstanceMapper, never()).updateById(any(SysApprovalInstanceDO.class));
        verify(contractMapper, never()).updateById(any(ContractDO.class));
    }

    /**
     * 反面对照：CAS 抢到了（影响 1 行）才允许往下走。
     *
     * <p>只断言「认领这一步确实被执行了，且没有抛 4005」。后续流程（推进节点 / 建下一节点待办）
     * 需要再铺一大片 mock，与本用例要保护的并发语义无关，这里刻意不铺 ——
     * 免得测试本身变成一坨脆弱的脚手架。
     */
    @Test
    void audit_whenTaskClaimSucceeds_passesTheClaim() {
        ContractDO contract = new ContractDO();
        contract.setContractId(1L);
        contract.setAuditStatus(1);
        when(contractMapper.selectById(1L)).thenReturn(contract);

        SysApprovalInstanceDO instance = new SysApprovalInstanceDO();
        instance.setInstanceId(8001L);
        when(sysApprovalInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(instance);

        SysApprovalTaskDO task = new SysApprovalTaskDO();
        task.setTaskId(9001L);
        task.setInstanceId(8001L);
        task.setApproverId(100L);
        task.setTaskStatus(0);
        when(sysApprovalTaskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(sysApprovalTaskMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        // 认领成功后流程会继续往下。下面这几个桩只是让流程能自然走到「没有下一节点 → 审批通过」
        // 的终点，本用例并不关心那一段的结果，只为证明认领这一关放行了。
        when(sysApprovalInstanceMapper.selectById(8001L)).thenReturn(instance);
        when(sysApprovalTaskMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(sysApprovalNodeMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        ContractAuditReqVO req = new ContractAuditReqVO();
        req.setResult(1);

        contractService.audit(1L, req);

        // 认领这一关放行了：CAS 被调用过
        verify(sysApprovalTaskMapper).update(any(), any(Wrapper.class));
    }

    // ==================== 关注 / 取消关注 ====================

    @Test
    void star_existingContract_delegatesToStarService() {
        ContractDO contract = new ContractDO();
        contract.setContractId(1L);
        when(contractMapper.selectById(1L)).thenReturn(contract);

        contractService.star(1L);

        verify(starService).star(StarBizType.CONTRACT, 1L);
    }

    @Test
    void star_missingContract_throws2301() {
        when(contractMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> contractService.star(999L))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(ErrorCode.CONTRACT_NOT_EXISTS.getCode());
        verify(starService, never()).star(anyInt(), any());
    }

    @Test
    void unstar_existingContract_delegatesToStarService() {
        ContractDO contract = new ContractDO();
        contract.setContractId(1L);
        when(contractMapper.selectById(1L)).thenReturn(contract);

        contractService.unstar(1L);

        verify(starService).unstar(StarBizType.CONTRACT, 1L);
    }

    // ==================== 分页 scope ====================

    /**
     * 「我关注的合同」在未关注任何合同时必须返回空页，且不查库 ——
     * 这同时是「不给 MyBatis-Plus 传空集合」的守卫（见 {@code applyScope}）。
     */
    @Test
    void pageQuery_scopeStarred_noStars_returnsEmptyWithoutQuery() {
        ContractPageReqVO req = new ContractPageReqVO();
        req.setScope("starred");
        when(starService.starredBizIds(StarBizType.CONTRACT)).thenReturn(Set.of());

        PageResult<ContractRespVO> result = contractService.pageQuery(req);

        assertThat(result.getList()).isEmpty();
        verify(contractMapper, never()).selectPage(any(PageParam.class), any(Wrapper.class));
    }
}
