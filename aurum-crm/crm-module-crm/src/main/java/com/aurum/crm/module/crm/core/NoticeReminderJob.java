package com.aurum.crm.module.crm.core;

import com.aurum.crm.module.crm.mapper.CustomerMapper;
import com.aurum.crm.module.crm.model.CustomerDO;
import com.aurum.crm.module.system.dal.dataobject.SysNoticeDO;
import com.aurum.crm.module.system.dal.mysql.SysNoticeMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 待办提醒定时任务：把「到期未回访的客户」生成消息（{@code sys_notice} 的「任务提醒」）。
 *
 * <p>数据来源不是 {@code sys_notice} 本身，而是 {@code crm_customer.next_contact_time}：
 * 每个归属人（{@code owner_user_id}）名下、{@code next_contact_time < 今天} 且尚未成交
 * （{@code deal_status = 未成交}）的客户，生成一条面向该归属人的「任务提醒」
 * （{@code notice_type = 4}，{@code receiver_id = owner_user_id}）。
 * 消息区块（{@link com.aurum.crm.module.crm.service.impl.DashboardServiceImpl#messages}）
 * 统一读 {@code sys_notice}，所以这里只负责「产」、不负责「展示」。
 *
 * <p><b>幂等</b>：同一客户（{@code biz_type=customer + biz_id}）只生成一条未处理提醒，
 * 下次扫描 `exists` 命中即跳过 —— 避免每天调度重复堆叠同一条消息。
 * 用户跟进后（回填新的 {@code next_contact_time}）会自然消失；若要重新提醒，
 * 需先清掉旧提醒行（或客户数据变化后由人工维护）。
 *
 * <p><b>create_by 为空</b>：本方法跑在定时线程，没有 {@code LoginUserHolder} 登录上下文，
 * {@code BaseDO.createBy} 填充为 {@code null} —— 对「系统生成」的消息这是预期行为。
 */
@Slf4j
@Component
public class NoticeReminderJob {

    /** 未成交（与 DashboardServiceImpl.DEAL_STATUS_UNDEALT 同含义，避免跨类耦合） */
    private static final Integer DEAL_STATUS_UNDEALT = 1;
    /** 任务提醒 */
    private static final int NOTICE_TYPE_TASK = 4;
    /** 关联业务类型：客户 */
    private static final String BIZ_TYPE_CUSTOMER = "customer";

    /** 分布式锁的名字，集群里唯一标识这个任务 */
    private static final String JOB_NAME = "notice-reminder";
    /**
     * 锁的自动过期时间。
     *
     * <p>任务本身是秒级的，给 30 分钟纯粹是兜底 —— 它只在「进程崩溃没来得及释放」
     * 或「执行时间远超预期」时起作用。宁可给大：给大了不影响正确性，
     * 给小了会在任务还没跑完时就放别人进来，那正是要避免的事。
     */
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    @Resource
    private CustomerMapper customerMapper;
    @Resource
    private SysNoticeMapper sysNoticeMapper;
    @Resource
    private DistributedJobLock jobLock;

    /**
     * 每天凌晨 03:05 跑一次（错开整点，避免和别的凌晨任务挤在一起）。
     *
     * <p>cron 用非整点分钟是为遵循「避免 :00/:30 整点瞬时并发」的约定；
     * <b>同一个实例内</b>重复触发也不会有副作用（幂等，见类注释）。
     *
     * <p><b>集群保护</b>：{@code @Scheduled} 是每个 JVM 各跑各的，应用部署两台以上时
     * 这个任务会在两台机器上同时执行。而本方法的幂等（{@link #noticeExists}）是
     * 「先查后插」—— 在各自的事务里双方都查到"不存在"，然后各插一条；
     * {@code sys_notice} 上又只有非唯一索引 {@code idx_biz}，数据库也拦不住。
     * 所以外面套一把分布式锁，只有抢到的实例执行，其余直接跳过。
     *
     * <p>⚠️ <b>锁在事务提交之前释放</b>：{@code finally} 在方法体内，而事务由 Spring
     * 在方法返回之后才提交。理论上这中间有个窗口 —— 别的实例拿到锁后可能读不到
     * 本次尚未提交的写入，从而插出重复提醒。这里可以接受，因为每个实例的 cron
     * <b>只触发一次</b>、抢不到就退出且不重试，窗口期内根本没有人来抢。
     * 真要彻底消除，得把 {@code @Transactional} 挪到另一个 Bean 上
     * （同类内自调用不走代理，事务会失效）—— 为 20 行任务加一个类不划算。
     */
    @Scheduled(cron = "0 5 3 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void generatePendingVisitReminders() {
        Optional<String> token = jobLock.tryLock(JOB_NAME, LOCK_TTL);
        if (token.isEmpty()) {
            log.info("[待办提醒] 另一个实例正在执行，本次跳过");
            return;
        }
        try {
            doGenerate();
        } finally {
            jobLock.unlock(JOB_NAME, token.get());
        }
    }

    /** 真正的扫描与生成。抽出来只为让上面那把锁的 try/finally 看着干净，事务仍在那一层 */
    private void doGenerate() {
        LocalDate today = LocalDate.now();
        List<CustomerDO> expired = customerMapper.selectList(
                Wrappers.<CustomerDO>lambdaQuery()
                        .isNotNull(CustomerDO::getNextContactTime)
                        .lt(CustomerDO::getNextContactTime, today)
                        .eq(CustomerDO::getDealStatus, DEAL_STATUS_UNDEALT)
                        .isNotNull(CustomerDO::getOwnerUserId));

        int created = 0;
        for (CustomerDO c : expired) {
            // 幂等：同一客户不重复生成「待回访」提醒
            if (noticeExists(c.getCustomerId())) {
                continue;
            }
            sysNoticeMapper.insert(buildNotice(c));
            created++;
        }

        if (created > 0) {
            log.info("[待办提醒] 生成 {} 条到期待回访客户提醒（共扫描 {} 个过期未成交客户）",
                    created, expired.size());
        }
    }

    /** 同一客户（biz_type=customer、biz_id）是否已有未处理的任务提醒 */
    private boolean noticeExists(Long customerId) {
        return sysNoticeMapper.selectCount(Wrappers.<SysNoticeDO>lambdaQuery()
                .eq(SysNoticeDO::getBizType, BIZ_TYPE_CUSTOMER)
                .eq(SysNoticeDO::getBizId, customerId)
                .eq(SysNoticeDO::getNoticeType, NOTICE_TYPE_TASK)) > 0;
    }

    private SysNoticeDO buildNotice(CustomerDO c) {
        SysNoticeDO n = new SysNoticeDO();
        n.setTitle("客户待回访：" + c.getCustomerName());
        n.setContent("客户「" + c.getCustomerName() + "」下次联系时间已到期，请及时跟进。");
        n.setNoticeType(NOTICE_TYPE_TASK);
        n.setBizType(BIZ_TYPE_CUSTOMER);
        n.setBizId(c.getCustomerId());
        n.setReceiverId(c.getOwnerUserId());
        n.setIsRead(0);
        n.setSendTime(LocalDateTime.now());
        return n;
    }
}
