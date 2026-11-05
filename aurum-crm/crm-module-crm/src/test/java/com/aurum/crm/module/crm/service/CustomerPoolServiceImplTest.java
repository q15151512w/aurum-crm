package com.aurum.crm.module.crm.service;

import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.mapper.CustomerMapper;
import com.aurum.crm.module.crm.mapper.OwnerChangeLogMapper;
import com.aurum.crm.module.crm.mapper.SysUserMiniMapper;
import com.aurum.crm.module.crm.model.CustomerDO;
import com.aurum.crm.module.crm.model.OwnerChangeLogDO;
import com.aurum.crm.module.crm.model.SysUserMiniDO;
import com.aurum.crm.module.crm.model.convert.CustomerConvert;
import com.aurum.crm.module.crm.model.dto.CustomerPoolAssignReqVO;
import com.aurum.crm.module.crm.service.impl.CustomerPoolServiceImpl;
import com.aurum.crm.module.system.service.OssStorageService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 公海池 Service 单元测试：领取 / 分配 / 删除的边界行为。
 */
@ExtendWith(MockitoExtension.class)
class CustomerPoolServiceImplTest {

    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private OwnerChangeLogMapper ownerChangeLogMapper;
    @Mock
    private SysUserMiniMapper sysUserMiniMapper;
    @Mock
    private CustomerConvert customerConvert;
    /**
     * ⚠️ 这个 mock 不能省。
     *
     * <p>{@code @InjectMocks} 只能注入<b>测试里声明过的</b> {@code @Mock}，缺声明的字段
     * 在单测里就是 {@code null}（Spring 在真实运行时会注入，所以只有单测能发现）。
     * {@code claim()} 的<b>第一行</b>就调 {@code poolClaimLimiter.checkBeforeClaim(2, 2, userId)}，
     * 漏了这个 mock 的话，claim 的两个用例会直接 NPE ——
     * 更难受的是 {@code claim_alreadyOwned_throws2103} 断言的是「抛 2103」，
     * 实际抛的是 NPE，报错信息只会说「期望 ServiceException 实际 NPE」，
     * **完全指不到「少声明了一个 mock」这个真正的原因**。
     */
    @Mock
    private PoolClaimLimiter poolClaimLimiter;
    /** 导出接口的依赖，同样只声明未打桩；不声明的话任何走到导出的用例会 NPE */
    @Mock
    private OssStorageService ossStorageService;

    @InjectMocks
    private CustomerPoolServiceImpl poolService;

    @BeforeEach
    void setUp() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(100L);
        loginUser.setDeptId(10L);
        LoginUserHolder.set(loginUser);
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    private CustomerDO poolCustomer() {
        CustomerDO customerDO = new CustomerDO();
        customerDO.setCustomerId(1L);
        customerDO.setCustomerName("公海客户");
        customerDO.setOwnerUserId(null);
        return customerDO;
    }

    private SysUserMiniDO miniUser(Long userId, Long deptId) {
        SysUserMiniDO user = new SysUserMiniDO();
        user.setUserId(userId);
        user.setRealName("李四" + userId);
        user.setDeptId(deptId);
        return user;
    }

    // ==================== 领取 ====================

    @Test
    void claim_setsCurrentUserAndDept() {
        when(customerMapper.selectById(1L)).thenReturn(poolCustomer());
        when(customerMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        poolService.claim(1L);

        ArgumentCaptor<CustomerDO> captor = ArgumentCaptor.forClass(CustomerDO.class);
        verify(customerMapper).update(captor.capture(), any(Wrapper.class));
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(100L);
        assertThat(captor.getValue().getDeptId()).isEqualTo(10L);

        ArgumentCaptor<OwnerChangeLogDO> logCaptor = ArgumentCaptor.forClass(OwnerChangeLogDO.class);
        verify(ownerChangeLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo(2);
    }

    @Test
    void claim_alreadyOwned_throws2103() {
        CustomerDO owned = poolCustomer();
        owned.setOwnerUserId(88L);
        when(customerMapper.selectById(1L)).thenReturn(owned);

        assertThatThrownBy(() -> poolService.claim(1L))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(ErrorCode.CUSTOMER_ALREADY_OWNED.getCode());
        verify(customerMapper, never()).update(any(), any());
    }

    /**
     * 领取上限必须在**碰任何数据之前**校验。
     *
     * <p>这条路径此前完全没有测试覆盖 —— {@code PoolClaimLimiter} 是后加进
     * {@code claim()} 的依赖，测试没同步补 mock，用例直接在它上面 NPE，
     * 于是「上限到底有没有生效」从没被验证过。
     *
     * <p>顺序断言不是形式主义：如果哪天有人把校验挪到查询之后，
     * 已超限的用户依然会先把客户记录读出来（还白跑一次数据权限），
     * 而结果看起来一样 —— 只有断言顺序能挡住这种退化。
     */
    @Test
    void claim_checksLimitBeforeTouchingAnyData() {
        when(customerMapper.selectById(1L)).thenReturn(poolCustomer());
        when(customerMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        poolService.claim(1L);

        // poolType=2 客户池 / bizType=2 客户 / 领取人=当前登录用户
        verify(poolClaimLimiter).checkBeforeClaim(2, 2, 100L);
        var ordered = inOrder(poolClaimLimiter, customerMapper);
        ordered.verify(poolClaimLimiter).checkBeforeClaim(2, 2, 100L);
        ordered.verify(customerMapper).selectById(1L);
    }

    /**
     * 超限时抛 2601，且**一个写操作都不能发生**。
     *
     * <p>这是 {@code PoolClaimLimiter} 在调用方的契约：它只负责拦，
     * 拦住之后调用方必须干净退出 —— 不能留下一条归属流转日志、
     * 更不能已经把 owner 改掉。
     */
    @Test
    void claim_limitReached_throws2601AndWritesNothing() {
        doThrow(new ServiceException(ErrorCode.POOL_CLAIM_LIMIT, "已达周期领取上限（3 条）"))
                .when(poolClaimLimiter).checkBeforeClaim(2, 2, 100L);

        assertThatThrownBy(() -> poolService.claim(1L))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(ErrorCode.POOL_CLAIM_LIMIT.getCode());

        verify(customerMapper, never()).selectById(any());
        verify(customerMapper, never()).update(any(), any());
        verify(ownerChangeLogMapper, never()).insert(any(OwnerChangeLogDO.class));
    }

    // ==================== 分配 ====================

    @Test
    void assign_usesTargetUserDeptAndAction1() {
        when(customerMapper.selectById(1L)).thenReturn(poolCustomer());
        when(sysUserMiniMapper.selectById(200L)).thenReturn(miniUser(200L, 30L));
        when(customerMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        CustomerPoolAssignReqVO reqVO = new CustomerPoolAssignReqVO();
        reqVO.setOwnerUserId(200L);
        reqVO.setReasonRemark("分给华东组");
        poolService.assign(1L, reqVO);

        // 回归：deptId 必须来自目标负责人（30），而不是操作人（10）
        ArgumentCaptor<CustomerDO> captor = ArgumentCaptor.forClass(CustomerDO.class);
        verify(customerMapper).update(captor.capture(), any(Wrapper.class));
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(200L);
        assertThat(captor.getValue().getDeptId()).isEqualTo(30L);

        ArgumentCaptor<OwnerChangeLogDO> logCaptor = ArgumentCaptor.forClass(OwnerChangeLogDO.class);
        verify(ownerChangeLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo(1);
        assertThat(logCaptor.getValue().getToOwnerId()).isEqualTo(200L);
        assertThat(logCaptor.getValue().getReasonRemark()).isEqualTo("分给华东组");
    }

    @Test
    void assign_targetUserMissing_throws2105() {
        when(customerMapper.selectById(1L)).thenReturn(poolCustomer());
        when(sysUserMiniMapper.selectById(999L)).thenReturn(null);

        CustomerPoolAssignReqVO reqVO = new CustomerPoolAssignReqVO();
        reqVO.setOwnerUserId(999L);

        assertThatThrownBy(() -> poolService.assign(1L, reqVO))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(ErrorCode.CUSTOMER_TARGET_USER_NOT_EXISTS.getCode());
        verify(customerMapper, never()).update(any(), any());
    }

    @Test
    void assign_alreadyOwned_throws2103() {
        CustomerDO owned = poolCustomer();
        owned.setOwnerUserId(88L);
        when(customerMapper.selectById(1L)).thenReturn(owned);

        CustomerPoolAssignReqVO reqVO = new CustomerPoolAssignReqVO();
        reqVO.setOwnerUserId(200L);

        assertThatThrownBy(() -> poolService.assign(1L, reqVO))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(ErrorCode.CUSTOMER_ALREADY_OWNED.getCode());
        verify(customerMapper, never()).update(any(), any());
    }

    // ==================== 删除 ====================

    @Test
    void delete_poolCustomer_ok() {
        when(customerMapper.selectById(1L)).thenReturn(poolCustomer());
        poolService.delete(1L);
        verify(customerMapper).deleteById(1L);
    }

    @Test
    void delete_ownedCustomer_throws2102() {
        CustomerDO owned = poolCustomer();
        owned.setOwnerUserId(88L);
        when(customerMapper.selectById(1L)).thenReturn(owned);

        assertThatThrownBy(() -> poolService.delete(1L))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(ErrorCode.CUSTOMER_NOT_IN_POOL.getCode());
        verify(customerMapper, never()).deleteById(any());
    }
}
