package com.aurum.crm.module.system.service.impl;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.dal.dataobject.SysOperationLogDO;
import com.aurum.crm.module.system.dal.mysql.SysOperationLogMapper;
import com.aurum.crm.module.system.model.OperationBusinessType;
import com.aurum.crm.module.system.model.OperationResult;
import com.aurum.crm.module.system.model.dto.OperationLogPageReqVO;
import com.aurum.crm.module.system.model.vo.OperationLogRespVO;
import com.aurum.crm.module.system.service.OperationLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志服务实现。
 *
 * <p><b>本类是只读的</b>（除了切面调用的 {@link #record}）：操作日志是审计数据，
 * 没有任何接口能修改或删除它 —— 唯一的写入口是 {@code OperationLogAspect}。
 */
@Slf4j
@Service
public class OperationLogServiceImpl implements OperationLogService {

    @Resource
    private SysOperationLogMapper sysOperationLogMapper;

    // ========================================================================
    //  查询
    // ========================================================================

    @Override
    public PageResult<OperationLogRespVO> pageQuery(OperationLogPageReqVO reqVO) {
        Page<SysOperationLogDO> page = sysOperationLogMapper.selectPage(reqVO, queryWrapper(reqVO));

        List<OperationLogRespVO> list = page.getRecords().stream()
                .map(this::toRespVO)
                .collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    private LambdaQueryWrapper<SysOperationLogDO> queryWrapper(OperationLogPageReqVO reqVO) {
        LambdaQueryWrapper<SysOperationLogDO> wrapper = Wrappers.lambdaQuery();

        if (StringUtils.hasText(reqVO.getKeyword())) {
            String keyword = reqVO.getKeyword().trim();
            // 两个条件包在一个 and(...) 里：不包的话外层条件会被 or 短路掉
            wrapper.and(w -> w.like(SysOperationLogDO::getOperName, keyword)
                    .or().like(SysOperationLogDO::getTitle, keyword));
        }

        // 固定二级排序：log_id 兜底，保证同一秒内的多条记录翻页不串行
        wrapper.orderByDesc(SysOperationLogDO::getOperTime)
                .orderByDesc(SysOperationLogDO::getLogId);
        return wrapper;
    }

    /**
     * DO → VO。
     *
     * <p>字段名与表列名不同是<b>有意</b>的：前端契约就是这套名字，别为了「跟表对齐」改。
     * 两个枚举列（{@code businessType} / {@code status}）在这里转成中文标签。
     */
    private OperationLogRespVO toRespVO(SysOperationLogDO logDO) {
        OperationLogRespVO vo = new OperationLogRespVO();
        vo.setUserName(logDO.getOperName());
        vo.setModule(logDO.getTitle());
        vo.setAction(OperationBusinessType.labelOf(logDO.getBusinessType()));
        vo.setMethod(logDO.getMethod());
        vo.setParams(logDO.getOperParam());
        vo.setIp(logDO.getOperIp());
        vo.setCostMs(logDO.getCostTime());
        vo.setStatus(OperationResult.labelOf(logDO.getStatus()));
        vo.setOperateTime(logDO.getOperTime());
        return vo;
    }

    // ========================================================================
    //  写入（只由切面调用）
    // ========================================================================

    @Override
    public void record(SysOperationLogDO logDO) {
        try {
            sysOperationLogMapper.insert(logDO);
        } catch (Exception e) {
            // 切面外面还包了一层 try/catch，这里再兜一次是为了让「写失败」这件事
            // 在本类内就有明确归属，不依赖调用方记得兜
            log.warn("[操作日志] 落库失败，已忽略。method={}", logDO.getMethod(), e);
        }
    }
}
