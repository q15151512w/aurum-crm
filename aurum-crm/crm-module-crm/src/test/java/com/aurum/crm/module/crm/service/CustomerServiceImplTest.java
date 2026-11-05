package com.aurum.crm.module.crm.service;

import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.mapper.CustomerMapper;
import com.aurum.crm.module.crm.mapper.OwnerChangeLogMapper;
import com.aurum.crm.module.crm.mapper.SysUserMiniMapper;
import com.aurum.crm.module.crm.model.CustomerDO;
import com.aurum.crm.module.crm.model.OwnerChangeLogDO;
import com.aurum.crm.module.crm.model.StarBizType;
import com.aurum.crm.module.crm.model.SysUserMiniDO;
import com.aurum.crm.module.crm.model.convert.CustomerConvert;
import com.aurum.crm.module.crm.model.dto.CustomerPageReqVO;
import com.aurum.crm.module.crm.model.dto.CustomerSaveReqVO;
import com.aurum.crm.module.crm.model.dto.ToPoolReqVO;
import com.aurum.crm.module.crm.model.vo.CustomerRespVO;
import com.aurum.crm.module.crm.service.StarService;
import com.aurum.crm.module.crm.service.BusinessNumberService;
import com.aurum.crm.module.crm.service.impl.CustomerServiceImpl;
import com.aurum.crm.mybatis.core.PageParam;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客户 Service 单元测试：增删改查 + 转公海的边界行为。
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

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
     * {@code CustomerServiceImpl#toPageResult} 会无条件调用
     * {@code starService.starredBizIds(...)} —— 漏了这个 mock 的话，任何走到分页的用例
     * 都报 {@code NullPointerException: ... "this.starService" is null}。
     *
     * <p>给 Service 加 {@code @Resource} 依赖时，顺手在这里补同名 {@code @Mock}。
     * 这个漏法没有编译期提示，只有一句指错方向的 NPE。
     */
    @Mock
    private StarService starService;
    @Mock
    private BusinessNumberService businessNumberService;

    @InjectMocks
    private CustomerServiceImpl customerService;

    @BeforeEach
    void setUp() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(100L);
        loginUser.setDeptId(10L);
        LoginUserHolder.set(loginUser);
        // 纯 Mockito 单测没有 Spring 上下文，LambdaUpdateWrapper 解析列名需要
        // MP 的 TableInfo 缓存，这里手动初始化（幂等，重复 init 无害）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, CustomerDO.class);
        TableInfoHelper.initTableInfo(assistant, OwnerChangeLogDO.class);
        TableInfoHelper.initTableInfo(assistant, SysUserMiniDO.class);
        // ⚠️ 单号桩不要放在这里。MockitoExtension 默认是 strict stubs，
        //    setUp 里打了桩却没被用到的用例会在 afterEach 抛 UnnecessaryStubbingException
        //    —— 报错指向的是 setUp 这一行，但真正的问题在那个**没用到它**的用例，
        //    方向完全被带偏（本文件曾因此 13 个用例红 11 个）。
        //    只有真正走到 businessNumberService.next("CUSTOMER") 的用例才在下面对应位置打桩。
        //    也别图省事改成 lenient()：那只是把提示静音，桩和用例的对应关系依旧看不出来。
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    private CustomerDO ownedCustomer(Long owner, Long dept) {
        CustomerDO customerDO = new CustomerDO();
        customerDO.setCustomerId(1L);
        customerDO.setCustomerName("北京星辰科技");
        customerDO.setOwnerUserId(owner);
        customerDO.setDeptId(dept);
        return customerDO;
    }

    private SysUserMiniDO miniUser(Long userId, Long deptId) {
        SysUserMiniDO user = new SysUserMiniDO();
        user.setUserId(userId);
        user.setRealName("张三" + userId);
        user.setDeptId(deptId);
        return user;
    }

    private int codeOf(ServiceException e) {
        return e.getCode();
    }

    // ==================== 新增 ====================

    @Test
    void create_defaultsOwnerToCurrentUser() {
        CustomerSaveReqVO reqVO = new CustomerSaveReqVO();
        reqVO.setCustomerName("测试客户");
        when(customerConvert.toDO(reqVO)).thenReturn(new CustomerDO());
        when(businessNumberService.next("CUSTOMER")).thenReturn("KH20260914000001");

        customerService.create(reqVO);

        ArgumentCaptor<CustomerDO> captor = ArgumentCaptor.forClass(CustomerDO.class);
        verify(customerMapper).insert(captor.capture());
        CustomerDO saved = captor.getValue();
        assertThat(saved.getOwnerUserId()).isEqualTo(100L);
        assertThat(saved.getDeptId()).isEqualTo(10L);
        assertThat(saved.getCustomerNo()).startsWith("KH");
    }

    @Test
    void create_explicitOwner_usesOwnerDept() {
        CustomerSaveReqVO reqVO = new CustomerSaveReqVO();
        reqVO.setCustomerName("测试客户");
        reqVO.setOwnerUserId(200L);
        CustomerDO converted = new CustomerDO();
        converted.setOwnerUserId(200L);
        when(customerConvert.toDO(reqVO)).thenReturn(converted);
        when(sysUserMiniMapper.selectById(200L)).thenReturn(miniUser(200L, 30L));
        // create() 走到最后一定会取单号，所以这里必须打桩
        when(businessNumberService.next("CUSTOMER")).thenReturn("KH20260914000002");

        customerService.create(reqVO);

        ArgumentCaptor<CustomerDO> captor = ArgumentCaptor.forClass(CustomerDO.class);
        verify(customerMapper).insert(captor.capture());
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(200L);
        assertThat(captor.getValue().getDeptId()).isEqualTo(30L);
    }

    @Test
    void create_explicitOwner_missing_throws2105() {
        CustomerSaveReqVO reqVO = new CustomerSaveReqVO();
        reqVO.setCustomerName("测试客户");
        reqVO.setOwnerUserId(999L);
        CustomerDO converted = new CustomerDO();
        converted.setOwnerUserId(999L);
        when(customerConvert.toDO(reqVO)).thenReturn(converted);
        when(sysUserMiniMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> customerService.create(reqVO))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> codeOf((ServiceException) e))
                .isEqualTo(ErrorCode.CUSTOMER_TARGET_USER_NOT_EXISTS.getCode());
        verify(customerMapper, never()).insert(any(CustomerDO.class));
    }

    // ==================== 修改 ====================

    @Test
    void update_reassignOwner_syncsDeptAndLogsAction5() {
        when(customerMapper.selectById(1L)).thenReturn(ownedCustomer(100L, 10L));
        CustomerSaveReqVO reqVO = new CustomerSaveReqVO();
        reqVO.setOwnerUserId(200L);
        when(customerConvert.toDO(reqVO)).thenReturn(new CustomerDO());
        when(sysUserMiniMapper.selectById(200L)).thenReturn(miniUser(200L, 30L));

        customerService.update(1L, reqVO);

        ArgumentCaptor<CustomerDO> captor = ArgumentCaptor.forClass(CustomerDO.class);
        verify(customerMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDeptId()).isEqualTo(30L);

        ArgumentCaptor<OwnerChangeLogDO> logCaptor = ArgumentCaptor.forClass(OwnerChangeLogDO.class);
        verify(ownerChangeLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo(5);
        assertThat(logCaptor.getValue().getFromOwnerId()).isEqualTo(100L);
        assertThat(logCaptor.getValue().getToOwnerId()).isEqualTo(200L);
    }

    @Test
    void update_sameOwner_noLogAndNoDeptSync() {
        when(customerMapper.selectById(1L)).thenReturn(ownedCustomer(100L, 10L));
        CustomerSaveReqVO reqVO = new CustomerSaveReqVO();
        reqVO.setOwnerUserId(100L);
        when(customerConvert.toDO(reqVO)).thenReturn(new CustomerDO());

        customerService.update(1L, reqVO);

        verify(ownerChangeLogMapper, never()).insert(any(OwnerChangeLogDO.class));
        verify(sysUserMiniMapper, never()).selectById(any());
    }

    @Test
    void update_reassignFromPool_isAssignAction1() {
        when(customerMapper.selectById(1L)).thenReturn(ownedCustomer(null, null));
        CustomerSaveReqVO reqVO = new CustomerSaveReqVO();
        reqVO.setOwnerUserId(200L);
        when(customerConvert.toDO(reqVO)).thenReturn(new CustomerDO());
        when(sysUserMiniMapper.selectById(200L)).thenReturn(miniUser(200L, 30L));

        customerService.update(1L, reqVO);

        ArgumentCaptor<OwnerChangeLogDO> logCaptor = ArgumentCaptor.forClass(OwnerChangeLogDO.class);
        verify(ownerChangeLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo(1);
    }

    @Test
    void update_reassignMissingTarget_throws2105() {
        when(customerMapper.selectById(1L)).thenReturn(ownedCustomer(100L, 10L));
        CustomerSaveReqVO reqVO = new CustomerSaveReqVO();
        reqVO.setOwnerUserId(999L);
        when(customerConvert.toDO(reqVO)).thenReturn(new CustomerDO());
        when(sysUserMiniMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> customerService.update(1L, reqVO))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> codeOf((ServiceException) e))
                .isEqualTo(ErrorCode.CUSTOMER_TARGET_USER_NOT_EXISTS.getCode());
        verify(customerMapper, never()).updateById(any(CustomerDO.class));
    }

    @Test
    void update_missingCustomer_throws2101() {
        when(customerMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> customerService.update(1L, new CustomerSaveReqVO()))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> codeOf((ServiceException) e))
                .isEqualTo(ErrorCode.CUSTOMER_NOT_EXISTS.getCode());
    }

    // ==================== 转公海 ====================

    @Test
    void putToPool_alreadyInPool_throws2104() {
        when(customerMapper.selectById(1L)).thenReturn(ownedCustomer(null, null));

        assertThatThrownBy(() -> customerService.putToPool(1L, new ToPoolReqVO()))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> codeOf((ServiceException) e))
                .isEqualTo(ErrorCode.CUSTOMER_ALREADY_IN_POOL.getCode());
        verify(customerMapper, never()).update(any(), any());
    }

    @Test
    void putToPool_clearsOwnerViaUpdateWrapper() {
        when(customerMapper.selectById(1L)).thenReturn(ownedCustomer(100L, 10L));
        when(customerMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        customerService.putToPool(1L, new ToPoolReqVO());

        // 关键回归：置空必须走 UpdateWrapper 显式 set null，updateById 会忽略 null 字段。
        // 断言点：owner_user_id / dept_id 出现在 SET 子句里 —— 只有显式 .set(col, null)
        // 才会把它们带进 getSqlSet()（值走参数占位符，SQL 里是 #{...} 形式）。
        ArgumentCaptor<Wrapper<CustomerDO>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(customerMapper).update(any(), wrapperCaptor.capture());
        // UpdateWrapper 的 SET 部分在 getSqlSet()，getSqlSegment() 只有 WHERE
        String sqlSet = ((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CustomerDO>)
                wrapperCaptor.getValue()).getSqlSet();
        assertThat(sqlSet).contains("owner_user_id=");
        assertThat(sqlSet).contains("dept_id=");
        assertThat(sqlSet).contains("enter_pool_time=");
        assertThat(sqlSet).contains("prev_owner_id=");
        // WHERE 里带「负责人还是原值」的并发保护条件
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("owner_user_id");

        ArgumentCaptor<OwnerChangeLogDO> logCaptor = ArgumentCaptor.forClass(OwnerChangeLogDO.class);
        verify(ownerChangeLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo(3);
        assertThat(logCaptor.getValue().getFromOwnerId()).isEqualTo(100L);
        assertThat(logCaptor.getValue().getToOwnerId()).isNull();
    }

    @Test
    void putToPool_concurrentConflict_throws4902() {
        when(customerMapper.selectById(1L)).thenReturn(ownedCustomer(100L, 10L));
        when(customerMapper.update(any(), any(Wrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> customerService.putToPool(1L, new ToPoolReqVO()))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> codeOf((ServiceException) e))
                .isEqualTo(ErrorCode.RESOURCE_ALREADY_TAKEN.getCode());
    }

    // ==================== 分页 ====================

    @Test
    void pageQuery_keywordCoversPhone() {
        CustomerPageReqVO reqVO = new CustomerPageReqVO();
        reqVO.setKeyword("科技");
        Page<CustomerDO> page = new Page<>(1, 10);
        page.setRecords(Collections.emptyList());
        when(customerMapper.selectPage(any(PageParam.class), any(Wrapper.class))).thenReturn(page);
        // StarService 的契约是「为空返回空集」；mock 的缺省返回值是 null，
        // 不显式打桩的话，一旦本用例改成非空页就会在 starredIds.contains(...) 上 NPE。
        when(starService.starredBizIds(anyInt(), anyList())).thenReturn(Set.of());

        customerService.pageQuery(reqVO);

        ArgumentCaptor<Wrapper<CustomerDO>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(customerMapper).selectPage(any(PageParam.class), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertThat(sql).contains("phone");
        assertThat(sql).contains("customer_name");
    }

    /**
     * 回归：整页都是公海客户（{@code owner_user_id IS NULL}）时列表不能炸。
     *
     * <p>公海行对所有人可见（数据权限拦截器注入的每个条件都带 {@code owner_user_id IS NULL}），
     * 所以「某一页恰好全是公海客户」是能构造出来的 —— 比如按客户名搜索一个只在公海里的客户
     * （种子里是「武汉光谷信息技术」）。
     *
     * <p>此时 {@code ownerIds} 为空，{@code ownerNameMap} 返回空 Map，而调用处会拿
     * {@code null} 去 {@code get()} —— 空 Map 若用 {@code Map.of()}（不可变 MapN），
     * {@code get(null)} 会先 {@code requireNonNull(key)} 直接抛 NPE，整个接口 500。
     * 必须用 {@code Collections.emptyMap()}。
     */
    @Test
    void pageQuery_allRowsInPool_doesNotThrow() {
        CustomerPageReqVO reqVO = new CustomerPageReqVO();
        Page<CustomerDO> page = new Page<>(1, 10);
        CustomerDO poolCustomer = new CustomerDO();
        poolCustomer.setCustomerId(7L);
        poolCustomer.setCustomerName("武汉光谷信息技术");
        poolCustomer.setOwnerUserId(null);   // 公海客户：没有负责人
        page.setRecords(List.of(poolCustomer));
        page.setTotal(1L);
        when(customerMapper.selectPage(any(PageParam.class), any(Wrapper.class))).thenReturn(page);
        when(customerConvert.toRespVO(any(CustomerDO.class))).thenReturn(new CustomerRespVO());
        when(starService.starredBizIds(anyInt(), anyList())).thenReturn(Set.of());

        PageResult<CustomerRespVO> result = customerService.pageQuery(reqVO);

        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getOwnerName()).isNull();
    }
}
