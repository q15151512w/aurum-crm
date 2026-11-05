package com.aurum.crm.module.system.service.impl;

import com.aurum.crm.module.system.dal.dataobject.AiCallLogDO;
import com.aurum.crm.module.system.dal.mysql.AiCallLogMapper;
import com.aurum.crm.module.system.model.dto.AiCallLogRecord;
import com.aurum.crm.module.system.service.AiCallLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * AI 调用日志实现。
 *
 * <p>刻意不加 {@code @Transactional}：这是单条 INSERT，事务只是多两次往返；
 * 更要紧的是它在**流式响应结束之后**执行，套事务会让数据库连接多攥一会儿。
 *
 * <p>也刻意不用 {@code @Async}：项目全局避免异步（{@code OperationLogAspect} 有说明），
 * 且这里的耗时本来就微不足道。
 */
@Slf4j
@Service
public class AiCallLogServiceImpl implements AiCallLogService {

    /**
     * 摘要字段的最大长度。
     *
     * <p>DDL 是 {@code VARCHAR(500)}，写超了 MySQL 会直接
     * {@code Data too long for column} 报错。这里先截断，
     * 比让一条日志写失败强。
     */
    private static final int DIGEST_MAX = 500;

    /**
     * 应用场景：问答。
     *
     * <p>{@code sys_ai_call_log.scene} 的注释列了 5 个场景，
     * 本功能（AI 小助手对话）对应 {@code QA}。
     */
    private static final String SCENE_QA = "QA";

    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAILURE = 0;

    @Resource
    private AiCallLogMapper aiCallLogMapper;

    @Override
    public void record(AiCallLogRecord record) {
        try {
            AiCallLogDO logDO = new AiCallLogDO();
            logDO.setScene(SCENE_QA);
            logDO.setModelName(truncate(record.getModelName(), 64));
            logDO.setPromptTokens(record.getPromptTokens());
            logDO.setCompletionTokens(record.getCompletionTokens());
            logDO.setTotalTokens(record.getTotalTokens());
            // 单价表未接入，先记 0；将来接价格配置后再算
            logDO.setCost(BigDecimal.ZERO);
            logDO.setLatencyMs((int) Math.min(record.getLatencyMs(), Integer.MAX_VALUE));
            logDO.setStatus(record.isSuccess() ? STATUS_SUCCESS : STATUS_FAILURE);
            logDO.setErrorMsg(truncate(record.getErrorMsg(), DIGEST_MAX));
            logDO.setInputDigest(truncate(record.getInputDigest(), DIGEST_MAX));
            logDO.setOutputDigest(truncate(record.getOutputDigest(), DIGEST_MAX));
            logDO.setOperatorId(record.getOperatorId());
            aiCallLogMapper.insert(logDO);
        } catch (Exception e) {
            // 写日志失败绝不能影响用户正在进行的对话：
            // 观测手段反过来破坏被观测的业务，是最糟的一种失败方式。
            log.warn("[AI] 写调用日志失败（已忽略，不影响对话）：{}", e.getMessage());
        }
    }

    /** 截断到指定长度；null 原样返回。 */
    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
