package com.aurum.crm.module.crm.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 定时任务的分布式锁 —— 保证同一个任务在集群里**同一时刻只有一个实例在跑**。
 *
 * <p><b>为什么需要它</b>：{@code @Scheduled} 是<b>每个 JVM 各跑各的</b>，没有任何协调。
 * 单机时这没问题，一旦应用部署两台以上，每天 03:05 那个任务就会在两台机器上同时执行。
 * 而任务里的幂等判断（如 {@code NoticeReminderJob#noticeExists}）都是
 * 「先查后插」——在两台机器各自的事务里，双方都查到"不存在"，然后各插一条。
 * 数据库也拦不住：那些表上只有非唯一索引，没有唯一约束兜底。
 *
 * <p><b>用法</b>：
 * <pre>{@code
 * Optional<String> token = jobLock.tryLock("notice-reminder", Duration.ofMinutes(30));
 * if (token.isEmpty()) {
 *     return;                       // 别的实例正在跑，本次跳过
 * }
 * try {
 *     doWork();
 * } finally {
 *     jobLock.unlock("notice-reminder", token.get());
 * }
 * }</pre>
 *
 * <p><b>三个不显然的地方</b>：
 * <ol>
 *     <li><b>必须用 {@code setIfAbsent} 而不是「先 GET 再 SET」</b>。后者是
 *         check-then-act：两台机器可能同时查到"没有锁"，然后都认为自己拿到了。
 *         这与项目里状态流转一律用「条件 UPDATE + 判影响行数」是同一个道理 ——
 *         判断和执行必须是同一次原子操作。</li>
 *     <li><b>释放时要校验 token</b>。锁带 TTL，如果 A 执行得比 TTL 还久，
 *         锁会先过期、被 B 拿到；这时 A 执行完若无脑 {@code DEL}，
 *         删掉的就是 <b>B 的锁</b>，C 又能进来 —— 三个实例同时跑。
 *         所以每次加锁生成一个随机 token，释放时只删"还是自己那次拿到的"锁。</li>
 *     <li><b>「比对 + 删除」要用 Lua 保证原子</b>。写成 Java 里的「先 GET 比对、
 *         再 DEL」，两步之间锁同样可能过期换人，等于第 2 点白做。
 *         （与 {@code EmailCodeServiceImpl} 的验证码脚本同一个思路。）</li>
 * </ol>
 *
 * <p><b>Redis 挂掉时是「放行」而不是「跳过」</b>（fail-open）：
 * {@code tryLock} 遇到异常时返回一个可用的 token，让任务照常执行。
 * 理由是当前仍是单机部署 —— 跳过意味着当天的提醒<b>一条都不会发</b>，
 * 而放行最多是在多机+Redis 同时故障时出现重复，后者可恢复、前者不可。
 * 注意 Redis 挂掉时本应用其实已经不能登录了（Sa-Token 存在 Redis），
 * 这个取舍的实际影响很小。
 */
@Slf4j
@Component
public class DistributedJobLock {

    /** key 前缀。带 crm: 命名空间，与项目其它 key 保持一致的分组习惯 */
    private static final String KEY_PREFIX = "crm:job:lock:";

    /**
     * 释放锁的脚本：只有值仍等于自己那次拿到的 token 才删。
     *
     * <p>返回 1 = 确实删掉了自己的锁；0 = 锁已经不属于自己（已过期或已被别人拿到）。
     * 后者不是错误，是正常的超时情形，所以调用方只记日志不报错。
     */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public DistributedJobLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 尝试获取锁，拿不到就立刻返回，<b>不等待、不重试</b>。
     *
     * <p>定时任务的语义是「这次没轮到我就不做了」，不是「排队等它跑完」——
     * 排队会让第二个实例晚几秒重复执行一遍同样的扫描，那正是要避免的事。
     *
     * @param jobName 任务名，同一任务必须用同一个名字
     * @param ttl     锁的自动过期时间。应**明显大于**任务最长耗时；宁可给大，
     *                因为它只在"进程崩溃没来得及释放"时兜底，给大了也不影响正确性
     * @return 拿到锁时返回本次的 token（释放时要原样传回）；没拿到返回空
     */
    public Optional<String> tryLock(String jobName, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(KEY_PREFIX + jobName, token, ttl);
            if (Boolean.TRUE.equals(acquired)) {
                return Optional.of(token);
            }
            return Optional.empty();
        } catch (Exception e) {
            // fail-open，理由见类注释
            log.warn("[任务锁] 获取锁失败（{}），按放行处理，本次照常执行：{}", jobName, e.getMessage());
            return Optional.of(token);
        }
    }

    /**
     * 释放锁。拿不到自己的锁时只记日志 —— 那说明本次执行超过了 TTL，
     * 锁已被别人拿走，此时删它反而会破坏别人的互斥。
     */
    public void unlock(String jobName, String token) {
        try {
            Long released = redis.execute(RELEASE_SCRIPT, List.of(KEY_PREFIX + jobName), token);
            if (released == null || released == 0L) {
                log.warn("[任务锁] 释放 {} 时锁已不属于本次执行（可能执行时间超过了 TTL），未删除",
                        jobName);
            }
        } catch (Exception e) {
            // 释放失败不是业务错误：锁有 TTL 会自己过期，最坏情况是下一个周期被跳过
            log.warn("[任务锁] 释放锁失败（{}）：{}", jobName, e.getMessage());
        }
    }
}
