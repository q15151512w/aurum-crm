package com.aurum.crm.integration;

import com.aurum.crm.module.crm.mapper.SysUserMiniMapper;
import com.aurum.crm.module.crm.model.vo.SalesOwnerRespVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 销售负责人下拉接口的数据链路验证。
 *
 * <p>验证 {@code SysUserMiniMapper#selectSalesOwnerOptions()} 在真实库上
 * 能查到销售人员，字段映射（real_name → realName 等）也一并被验证。
 *
 * <p>运行前提：MySQL 与 Redis 已启动，且已执行过建表+种子数据 SQL。
 *
 * <p>⚠️ <b>本类直连 {@code dev} profile 指向的真实库，断言只能针对「契约」，
 * 不能针对「数据量」。</b> 原先这里写的是 {@code hasSize(3)}（种子里的
 * 销售总监 2 / 销售专员 3、4），看起来是个很自然的断言 —— 但它假设的是
 * 「刚播种完的干净库」，而实际跑的库早就有真实业务数据了（2026-09-15 实测为 12 人，
 * 多出来的是 apengjiang 和各销售组，另有一批非销售岗位的用户 —— 见下）。
 * 于是它每加一个销售就红一次，而且红得毫无意义。
 *
 * <p>这种「狼来了」测试比没有测试更糟：跑红几次之后，大家就学会忽略它了，
 * 真正的回归反而被淹没。断言要钉住的是「这个接口该保证什么」，不是「此刻有几行」。
 *
 * <p>⚠️ 另需知道：结果里<b>会</b>包含仓管/质检/采购/生产等非销售岗位的人。
 * 这不是 bug，也不是数据漂移 —— {@code sql/crm.sql} 的 {@code sys_user_role}
 * 种子注释写明「种子只有 4 个角色，非销售岗位暂挂它」，即那些账号被有意挂在
 * {@code sales} 角色上。要收窄下拉的取值范围，得先给这些岗位建角色，
 * 改这里或改断言都治不了本。
 */
@DisplayName("销售负责人下拉数据链路")
@ActiveProfiles("dev")
@SpringBootTest
class SalesOwnerOptionsTest {

    /** 种子里确定的销售岗：director / sales01 / sales02（见 sql/crm.sql 的 sys_user_role） */
    private static final List<Long> SEEDED_SALES_USER_IDS = List.of(2L, 3L, 4L);

    @Autowired
    private SysUserMiniMapper sysUserMiniMapper;

    @Test
    @DisplayName("能查到在职销售人员，且姓名/部门有值")
    void testSelectSalesOwnerOptions() {
        List<SalesOwnerRespVO> owners = sysUserMiniMapper.selectSalesOwnerOptions();

        assertThat(owners).as("销售负责人下拉不应为空").isNotEmpty();
        assertThat(owners).extracting(SalesOwnerRespVO::getRealName)
                .allSatisfy(name -> assertThat(name).isNotBlank());
        assertThat(owners).extracting(SalesOwnerRespVO::getDeptName)
                .as("下拉要按部门分组展示，dept_name 不能为空 —— 空了 JOIN 就是错的")
                .allSatisfy(dept -> assertThat(dept).isNotBlank());
        // 已知销售岗必须查得到 —— 挡住的是「JOIN 写坏 / 过滤过严 / 把销售也漏掉」。
        // ⚠️ 这条【挡不住】「过滤写宽成返回全表」：全表照样包含 2/3/4。
        //    要挡住那种退化，得反过来断言「返回的每个人都确实持有销售角色」，
        //    那需要在测试里再查一次角色表，代价大于收益 —— 如实记在这里，
        //    免得后来的人以为这条断言能兜住「下拉混进非销售」这类问题。
        assertThat(owners).extracting(SalesOwnerRespVO::getUserId)
                .as("种子里的销售岗必须在结果里")
                .containsAll(SEEDED_SALES_USER_IDS);

        System.out.printf("[验证] 销售人员数量=%d, 数据=%s%n", owners.size(), owners);
    }
}
