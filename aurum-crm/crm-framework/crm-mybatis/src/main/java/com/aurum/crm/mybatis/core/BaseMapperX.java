package com.aurum.crm.mybatis.core;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Collection;
import java.util.List;

/**
 * Mapper 基类，在 MyBatis-Plus 的 {@link BaseMapper} 上补几个高频方法。
 *
 * <p>所有业务 Mapper 继承本接口，不要直接继承 {@code BaseMapper}——
 * 这样后续要统一加能力（如数据权限的强制校验钩子）时，只需改这一处。
 *
 * <pre>{@code
 * @Mapper
 * public interface CustomerMapper extends BaseMapperX<CustomerDO> {
 * }
 * }</pre>
 *
 * @param <T> 实体类型
 */
public interface BaseMapperX<T> extends BaseMapper<T> {

    /**
     * 分页查询。
     *
     * <p>参数直接收 {@link PageParam}，省掉每次手写 {@code new Page<>(pageNo, pageSize)}。
     */
    default Page<T> selectPage(PageParam pageParam, Wrapper<T> queryWrapper) {
        return selectPage(pageParam.toPage(), queryWrapper);
    }

    /**
     * 按单个字段等值查询一条。
     *
     * <p><b>注意</b>：结果多于一条时会抛 {@code TooManyResultsException}。
     * 只在字段有唯一约束时使用（如 {@code dictType}、{@code contractNo}）。
     */
    default T selectOne(SFunction<T, ?> field, Object value) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field, value));
    }

    /** 按单个字段等值查询列表 */
    default List<T> selectList(SFunction<T, ?> field, Object value) {
        return selectList(new LambdaQueryWrapper<T>().eq(field, value));
    }

    /** 按单个字段 IN 查询列表 */
    default List<T> selectList(SFunction<T, ?> field, Collection<?> values) {
        return selectList(new LambdaQueryWrapper<T>().in(field, values));
    }

    /**
     * 按单个字段等值查询是否存在。
     *
     * <p>比 {@code selectCount(...) > 0} 更轻——SQL 走 {@code LIMIT 1}。
     */
    default boolean exists(SFunction<T, ?> field, Object value) {
        return selectCount(new LambdaQueryWrapper<T>().eq(field, value)) > 0;
    }
}
