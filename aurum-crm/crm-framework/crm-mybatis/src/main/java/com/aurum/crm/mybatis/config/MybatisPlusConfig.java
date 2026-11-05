package com.aurum.crm.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.aurum.crm.mybatis.core.DataPermissionInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p><b>⚠️ 拦截器顺序很重要</b>：MyBatis-Plus 的多个 InnerInterceptor 是链式执行的，
 * 顺序决定行为。这里的顺序是实测调出来的：
 * <ol>
 *     <li><b>数据权限拦截器必须在分页拦截器之前</b> —— 见下面的说明</li>
 *     <li>乐观锁放最后 —— 它只改 SET 部分，要在所有 WHERE 改写之后执行</li>
 * </ol>
 *
 * <p><b>为什么数据权限要在分页之前</b>（这条曾经写反过，是实测纠正的）：
 * 分页插件的 count 语句是它在 {@code beforeQuery} 里<b>基于当前 SQL</b> 生成的。
 * 若分页排在前面，它生成 count 时 SQL 还是「未加数据权限条件」的原样，
 * 于是 {@code SELECT COUNT(*)} 统计的是<b>全表</b> ——
 * 表现为「列表只返回 5 条，total 却是 9」，前端分页器页数全错，
 * 而且把不属于该用户的数据总量泄露了出去。
 * 数据权限排前面，count 就是从已经带上条件的 SQL 派生的，总数才对得上。
 */
@Configuration
// 两类包约定都要扫：system 模块用 dal.mysql，crm 模块用 mapper
@MapperScan({"com.aurum.crm.**.dal.mysql", "com.aurum.crm.**.mapper"})
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 数据权限拦截器 —— 按 data_scope 自动给业务表追加行级过滤。
        //    必须排在分页【之前】，否则分页的 count 不计入数据权限条件（总数虚高）。
        interceptor.addInnerInterceptor(new DataPermissionInnerInterceptor());

        // 2. 分页插件
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大条数限制，防止前端传 pageSize=999999 拖垮数据库
        pagination.setMaxLimit(500L);
        // 页码超过总页数时不自动跳回首页，直接返回空列表（前端体验更明确）
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        // 3. 防止全表更新与删除（误写 UPDATE 不带 WHERE 时直接拦截）
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        // 4. 乐观锁插件（配合实体上的 @Version 字段使用，领取并发会用到）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }
}
