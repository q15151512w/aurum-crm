package com.aurum.crm.module.crm.service.impl;

import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.mapper.StarMapper;
import com.aurum.crm.module.crm.model.StarBizType;
import com.aurum.crm.module.crm.model.StarDO;
import com.aurum.crm.module.crm.service.StarService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注 Service 实现：通用「关注 / 取消关注」，不绑定具体业务对象。
 *
 * <p>写入幂等，读方法无事务（与 {@code CustomerServiceImpl#pageQuery} 一致）。
 */
@Slf4j
@Service
public class StarServiceImpl implements StarService {

    @Resource
    private StarMapper starMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void star(int bizType, Long bizId) {
        checkBiz(bizType, bizId);
        Long userId = LoginUserHolder.getRequired().getUserId();
        if (isStarred(bizType, bizId)) {
            return; // 幂等：已关注就不写，也避免无谓地刷新审计字段
        }
        StarDO starDO = new StarDO();
        starDO.setBizType(bizType);
        starDO.setBizId(bizId);
        starDO.setUserId(userId);
        try {
            starMapper.insert(starDO);
        } catch (DuplicateKeyException e) {
            // 并发双击：两个请求同时越过上面的存在性判断，靠 uk_biz_user 兜底，后到的撞唯一键。
            // 语义上「已关注」已经达成，吞掉即可 —— 不能让它冒到 GlobalExceptionHandler：
            // 那里只处理 ServiceException / 参数校验 / 兜底 Exception，没有 DuplicateKeyException
            // 分支，漏出去会变成 500「服务器内部错误」。
            log.debug("[关注] 并发重复关注，已忽略。bizType={}, bizId={}, userId={}",
                    bizType, bizId, userId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unstar(int bizType, Long bizId) {
        checkBiz(bizType, bizId);
        Long userId = LoginUserHolder.getRequired().getUserId();
        // 走 @TableLogic，实际是 UPDATE ... SET del_flag = 1；带 WHERE 不会触发 BlockAttackInnerInterceptor。
        // 未关注过时影响 0 行，不报错 —— 幂等。
        starMapper.delete(Wrappers.<StarDO>lambdaQuery()
                .eq(StarDO::getBizType, bizType)
                .eq(StarDO::getBizId, bizId)
                .eq(StarDO::getUserId, userId));
    }

    @Override
    public boolean isStarred(int bizType, Long bizId) {
        Long userId = LoginUserHolder.getRequired().getUserId();
        return starMapper.selectCount(Wrappers.<StarDO>lambdaQuery()
                .eq(StarDO::getBizType, bizType)
                .eq(StarDO::getBizId, bizId)
                .eq(StarDO::getUserId, userId)) > 0;
    }

    @Override
    public Set<Long> starredBizIds(int bizType, Collection<Long> bizIds) {
        if (bizIds == null || bizIds.isEmpty()) {
            // MyBatis-Plus 3.5.7 的 in() 对空集合会生成非法的 IN ()，必须先挡。
            return Set.of();
        }
        Long userId = LoginUserHolder.getRequired().getUserId();
        List<StarDO> stars = starMapper.selectList(Wrappers.<StarDO>lambdaQuery()
                .eq(StarDO::getUserId, userId)
                .eq(StarDO::getBizType, bizType)
                .in(StarDO::getBizId, bizIds));
        return stars.stream().map(StarDO::getBizId).collect(Collectors.toSet());
    }

    @Override
    public Set<Long> starredBizIds(int bizType) {
        Long userId = LoginUserHolder.getRequired().getUserId();
        List<StarDO> stars = starMapper.selectList(Wrappers.<StarDO>lambdaQuery()
                .eq(StarDO::getUserId, userId)
                .eq(StarDO::getBizType, bizType));
        return stars.stream().map(StarDO::getBizId).collect(Collectors.toSet());
    }

    /** 业务类型 + 业务ID 校验：未登记的类型一律拒绝，避免脏数据写进通用表 */
    private void checkBiz(int bizType, Long bizId) {
        if (!StarBizType.isSupported(bizType) || bizId == null) {
            throw new ServiceException(ErrorCode.STAR_BIZ_TYPE_INVALID);
        }
    }
}
