package com.aurum.crm.integration;

import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.module.system.dal.dataobject.DictTypeDO;
import com.aurum.crm.module.system.dal.mysql.DictTypeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 持久层基础设施验证。
 *
 * <p>本测试用一张真实表（{@code sys_dict_type}）走一遍完整流程，
 * 验证骨架的持久层能力是否按设计工作：
 *
 * <ol>
 *     <li><b>雪花主键</b> —— {@code id-type: ASSIGN_ID} 是否真的生成主键</li>
 *     <li><b>审计字段自动填充</b> —— {@code DefaultDBFieldHandler} 是否生效</li>
 *     <li><b>逻辑删除</b> —— {@code @TableLogic} 是否把 DELETE 转成 UPDATE</li>
 *     <li><b>Mapper 扫描路径</b> —— {@code @MapperScan} 是否能扫到 Mapper</li>
 *     <li><b>生成列 + 唯一索引方案</b> —— 逻辑删除后同编号能否重新插入</li>
 * </ol>
 *
 * <p><b>为什么要手动清理数据</b>：没有加 {@code @Transactional}——
 * 加了会让操作在事务里回滚，就观察不到「真实落库状态」（尤其是逻辑删除后
 * {@code del_flag} 到底有没有真的写进去）。所以改为在 {@code @AfterEach} 里
 * 用 JdbcTemplate 物理删除测试数据。
 *
 * <p>运行前提：MySQL 与 Redis 已启动，且已执行过 {@code crm-schema-V1.1.sql}。
 */
@DisplayName("持久层基础设施验证")
@ActiveProfiles("dev")
@SpringBootTest
class PersistenceLayerTest {

    /** 测试数据前缀，避免与初始化数据（crm_source 等）冲突 */
    private static final String TEST_PREFIX = "TEST_";

    private static final Long TEST_USER_ID = 99999L;
    private static final Long TEST_DEPT_ID = 1L;

    @Autowired
    private DictTypeMapper dictTypeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanTestData();
        // 模拟登录上下文 —— 审计字段 createBy / updateBy 从这里取值
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(TEST_USER_ID);
        loginUser.setUsername("test-user");
        loginUser.setDeptId(TEST_DEPT_ID);
        LoginUserHolder.set(loginUser);
    }

    @AfterEach
    void tearDown() {
        // ⚠️ 必须清理：ThreadLocal 在测试线程复用时会串号
        LoginUserHolder.clear();
        cleanTestData();
    }

    // ========================================================================
    //  1. 雪花主键
    // ========================================================================

    @Test
    @DisplayName("插入时自动生成雪花主键（不需要手动 setId）")
    void testSnowflakeIdGenerated() {
        DictTypeDO dictType = buildDictType("SNOWFLAKE", "雪花ID测试");

        // 关键：这里【没有】调用 dictType.setDictId(...)
        int rows = dictTypeMapper.insert(dictType);

        assertThat(rows).isEqualTo(1);
        assertThat(dictType.getDictId())
                .as("主键应由 ASSIGN_ID 自动生成，而非 null")
                .isNotNull()
                .isPositive();

        // 雪花 ID 是 64 位整数，生成的 ID 应该有 18~19 位十进制
        String idStr = String.valueOf(dictType.getDictId());
        assertThat(idStr.length())
                .as("雪花 ID 长度应在 18~19 位之间，实际为 %s", idStr)
                .isBetween(18, 19);

        System.out.printf("[验证] 自动生成的主键 dictId = %s%n", dictType.getDictId());
    }

    // ========================================================================
    //  2. 审计字段自动填充
    // ========================================================================

    @Test
    @DisplayName("审计字段自动填充（createBy/createTime/updateBy/updateTime/delFlag）")
    void testAuditFieldsAutoFilled() {
        DictTypeDO dictType = buildDictType("AUDIT", "审计字段测试");

        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        dictTypeMapper.insert(dictType);
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertThat(dictType.getCreateBy()).as("createBy").isEqualTo(TEST_USER_ID);
        assertThat(dictType.getUpdateBy()).as("updateBy").isEqualTo(TEST_USER_ID);
        assertThat(dictType.getDelFlag()).as("delFlag").isZero();

        assertThat(dictType.getCreateTime()).as("createTime").isNotNull();
        assertThat(dictType.getCreateTime())
                .as("createTime 应落在插入前后之间")
                .isAfter(before)
                .isBefore(after);

        System.out.printf("[验证] createBy=%d, createTime=%s, delFlag=%d%n",
                dictType.getCreateBy(), dictType.getCreateTime(), dictType.getDelFlag());
    }

    @Test
    @DisplayName("更新时刷新 updateBy / updateTime，且不覆盖 createBy / createTime")
    void testUpdateFillsUpdateFields() throws InterruptedException {
        DictTypeDO dictType = buildDictType("UPDATE", "更新测试");
        dictTypeMapper.insert(dictType);

        Long originalCreateBy = dictType.getCreateBy();
        LocalDateTime originalCreateTime = dictType.getCreateTime();

        // 等 1.1 秒，确保 updateTime 与 createTime 能区分开
        Thread.sleep(1100);

        dictType.setDictName("更新后的名称");
        dictTypeMapper.updateById(dictType);

        DictTypeDO updated = dictTypeMapper.selectById(dictType.getDictId());
        assertThat(updated.getDictName()).isEqualTo("更新后的名称");
        assertThat(updated.getCreateBy()).as("createBy 不应被覆盖").isEqualTo(originalCreateBy);
        assertThat(updated.getCreateTime()).as("createTime 不应被覆盖").isEqualTo(originalCreateTime);
        assertThat(updated.getUpdateTime())
                .as("updateTime 应晚于 createTime")
                .isAfter(originalCreateTime);

        System.out.printf("[验证] createTime=%s → updateTime=%s%n",
                originalCreateTime, updated.getUpdateTime());
    }

    // ========================================================================
    //  3. 逻辑删除
    // ========================================================================

    @Test
    @DisplayName("逻辑删除：selectById 查不到，但数据仍在库里且 del_flag=1")
    void testLogicalDelete() {
        DictTypeDO dictType = buildDictType("LOGIC_DEL", "逻辑删除测试");
        dictTypeMapper.insert(dictType);
        Long dictId = dictType.getDictId();

        // 逻辑删除（MyBatis-Plus 会转成 UPDATE sys_dict_type SET del_flag=1）
        dictTypeMapper.deleteById(dictId);

        // ① 通过 Mapper 查不到（@TableLogic 自动追加 del_flag = 0）
        assertThat(dictTypeMapper.selectById(dictId))
                .as("@TableLogic 应让查询自动过滤已删除数据")
                .isNull();

        // ② 绕开 ORM 直查数据库，数据还在，且 del_flag = 1
        Map<String, Object> raw = jdbcTemplate.queryForMap(
                "SELECT dict_id, del_flag, is_active FROM sys_dict_type WHERE dict_id = ?", dictId);
        // 用 Number 比较，避免 TINYINT 被驱动映射成 Byte/Short 导致断言失败
        assertThat(((Number) raw.get("del_flag")).intValue())
                .as("数据库中的 del_flag")
                .isEqualTo(1);
        assertThat(raw.get("is_active"))
                .as("生成列 is_active 在删除后应为 NULL（这正是唯一索引能复用的原因）")
                .isNull();

        System.out.printf("[验证] 逻辑删除后：ORM 查不到，库中 del_flag=%s, is_active=%s%n",
                raw.get("del_flag"), raw.get("is_active"));
    }

    // ========================================================================
    //  4. 生成列 + 唯一索引（解决「逻辑删除后编号仍被占用」）
    // ========================================================================

    @Test
    @DisplayName("唯一索引 + is_active 生成列：删除后同一编码可重新插入")
    void testUniqueConstraintReusableAfterLogicalDelete() {
        String dictTypeCode = TEST_PREFIX + "UNIQUE";
        DictTypeDO first = buildDictType("UNIQUE", "首次插入");
        dictTypeMapper.insert(first);
        assertThat(first.getDictId()).isNotNull();

        // ① 存活状态下重复插入同一编码 → 应被唯一索引拒绝
        DictTypeDO duplicate = buildDictType("UNIQUE", "重复插入");
        assertThatThrownBy(() -> dictTypeMapper.insert(duplicate))
                .as("存活行之间必须唯一")
                .hasMessageContaining("Duplicate entry");

        // ② 逻辑删除第一条
        dictTypeMapper.deleteById(first.getDictId());

        // ③ 现在可以重新插入同一编码了（is_active 变 NULL，NULL 之间不冲突）
        DictTypeDO reInsert = buildDictType("UNIQUE", "删除后重插");
        int rows = dictTypeMapper.insert(reInsert);

        assertThat(rows).isEqualTo(1);
        assertThat(reInsert.getDictId())
                .as("应生成新的主键，而非复用旧的")
                .isNotEqualTo(first.getDictId());

        System.out.printf("[验证] 同编码重插成功：旧 ID=%s（已删除）, 新 ID=%s%n",
                first.getDictId(), reInsert.getDictId());
    }

    // ========================================================================
    //  辅助
    // ========================================================================

    /**
     * 构造测试用字典类型。
     *
     * <p>⚠️ 编码统一由本方法拼上 {@link #TEST_PREFIX}，
     * <b>调用方不要自己写前缀</b> —— 否则 {@link #cleanTestData()} 的
     * {@code LIKE 'TEST_%'} 匹配不到，残留数据会让下次运行报唯一键冲突。
     *
     * @param codeSuffix 编码后缀（不含前缀），如 {@code "AUDIT"}
     */
    private DictTypeDO buildDictType(String codeSuffix, String dictName) {
        DictTypeDO dictTypeDO = new DictTypeDO();
        dictTypeDO.setDictType(TEST_PREFIX + codeSuffix);
        dictTypeDO.setDictName(dictName);
        dictTypeDO.setStatus(1);
        dictTypeDO.setRemark("集成测试数据，可安全删除");
        return dictTypeDO;
    }

    /** 物理删除测试数据（逻辑删除后行仍在库里，需绕过 ORM 清理） */
    private void cleanTestData() {
        jdbcTemplate.update("DELETE FROM sys_dict_type WHERE dict_type LIKE ?", TEST_PREFIX + "%");
    }
}
