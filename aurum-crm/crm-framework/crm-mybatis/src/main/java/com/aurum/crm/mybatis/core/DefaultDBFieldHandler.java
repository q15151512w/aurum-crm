package com.aurum.crm.mybatis.core;

import com.aurum.crm.common.core.LoginUserHolder;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 审计字段自动填充。
 *
 * <p>为什么用应用层填充而不是数据库的 {@code ON UPDATE CURRENT_TIMESTAMP}：
 * MySQL 在「本次 UPDATE 未改变任何列的值」时不会刷新时间戳，
 * 与「只要执行了更新就记录时间」的业务预期不符，且跨库行为不一致。
 *
 * <p>这里统一由应用层写入，好处是：
 * <ul>
 *     <li>行为确定，不受数据库版本影响</li>
 *     <li>可以和 {@code createBy} / {@code updateBy} 在同一处填充</li>
 *     <li>定时任务、数据迁移等无登录上下文的场景也能正常工作（此时填充为 null）</li>
 * </ul>
 */
@Component
public class DefaultDBFieldHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = now();
        Long userId = LoginUserHolder.getUserId();

        fillIfNull(metaObject, "createTime", now);
        fillIfNull(metaObject, "updateTime", now);
        fillIfNull(metaObject, "createBy", userId);
        fillIfNull(metaObject, "updateBy", userId);
        fillIfNull(metaObject, "delFlag", 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新时这两个字段必须覆盖，所以不用 fillIfNull
        setFieldValByName("updateTime", now(), metaObject);
        setFieldValByName("updateBy", LoginUserHolder.getUserId(), metaObject);
    }

    /**
     * 当前时间，<b>截断到秒</b>。
     *
     * <p>⚠️ 这里的截断不是可有可无的优化，而是必须的：
     * 建表脚本中审计字段用的是 {@code DATETIME}（<b>秒精度</b>，没有小数位），
     * 而 {@code LocalDateTime.now()} 带纳秒。若不截断会出现：
     * <pre>
     *   写入内存值  2026-09-12T18:30:45.123456789
     *   写入数据库  2026-09-12 18:30:45          ← MySQL 按列精度截断
     *   读回内存值  2026-09-12T18:30:45
     * </pre>
     * 结果是「写进去的值 ≠ 读出来的值」，任何「插入后用内存对象比对数据库」的
     * 逻辑（尤其是单元测试与乐观锁）都会莫名失败。
     *
     * <p>若业务需要毫秒精度，应把建表脚本的审计字段改为 {@code DATETIME(3)}，
     * 同时把这里改成 {@code truncatedTo(ChronoUnit.MILLIS)} —— 两边必须一致。
     */
    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * 只在字段为 null 时填充。
     *
     * <p>用 {@code fillIfNull} 而不是 {@code setFieldValByName}，是为了支持
     * 「数据迁移 / 补数据」场景 —— 那种情况下需要保留原始时间，不能被当前时间覆盖。
     */
    private void fillIfNull(MetaObject metaObject, String fieldName, Object value) {
        if (metaObject.hasSetter(fieldName) && getFieldValByName(fieldName, metaObject) == null) {
            setFieldValByName(fieldName, value, metaObject);
        }
    }
}
