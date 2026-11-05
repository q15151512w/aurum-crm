package com.aurum.crm.module.system.service.impl;

import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.system.dal.dataobject.SysNoticeDO;
import com.aurum.crm.module.system.dal.dataobject.SysUserDO;
import com.aurum.crm.module.system.dal.mysql.SysNoticeMapper;
import com.aurum.crm.module.system.dal.mysql.SysUserMapper;
import com.aurum.crm.module.system.model.dto.NoticePageReqVO;
import com.aurum.crm.module.system.model.dto.NoticeSaveReqVO;
import com.aurum.crm.module.system.model.vo.NoticeRespVO;
import com.aurum.crm.module.system.service.NoticeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 消息通知实现（《05-接口文档》3.6）。
 *
 * <p><b>可见性口径</b>：见 {@link SysNoticeDO}。核心规则 ——
 * {@code receiver_id = 我 OR receiver_id IS NULL}，从而管理端发的「全员公告」
 * （{@code receiver_id IS NULL}）能被每个登录用户读走。
 */
@Slf4j
@Service
public class NoticeServiceImpl implements NoticeService {

    /** 管理端（增删改）允许维护的类型：2 公海回收 / 3 系统公告 / 4 任务提醒 */
    private static final List<Integer> MANAGE_TYPE_IDS = Arrays.asList(2, 3, 4);

    private static final Integer READ = 1;

    @Resource
    private SysNoticeMapper sysNoticeMapper;
    @Resource
    private SysUserMapper sysUserMapper;

    // ------------------------------------------------------------------
    //  我的消息（面向登录用户）
    // ------------------------------------------------------------------

    @Override
    public PageResult<NoticeRespVO> pageQuery(NoticePageReqVO reqVO) {
        Long userId = LoginUserHolder.getUserId();
        if (userId == null) {
            return PageResult.empty(reqVO.getPageNo(), reqVO.getPageSize());
        }
        LambdaQueryWrapper<SysNoticeDO> wrapper = selfWrapped(reqVO, userId);
        Page<SysNoticeDO> page = sysNoticeMapper.selectPage(reqVO, wrapper);
        return toRespPage(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long id) {
        SysNoticeDO notice = getOwned(id);
        if (notice.getIsRead() != null && notice.getIsRead() == 1) {
            return; // 幂等：已读不重复写
        }
        if (sysNoticeMapper.update(null, Wrappers.<SysNoticeDO>lambdaUpdate()
                .set(SysNoticeDO::getIsRead, READ)
                .set(SysNoticeDO::getReadTime, LocalDateTime.now())
                .eq(SysNoticeDO::getNoticeId, id)) == 0) {
            throw new ServiceException(ErrorCode.NOTICE_NOT_EXISTS);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markHandled(Long id) {
        SysNoticeDO notice = getOwned(id);
        if (notice.getHandleStatus() != null && notice.getHandleStatus() == 1) {
            return; // 幂等：已处理不重复写
        }
        if (sysNoticeMapper.update(null, Wrappers.<SysNoticeDO>lambdaUpdate()
                .set(SysNoticeDO::getHandleStatus, 1)
                .set(SysNoticeDO::getHandleTime, LocalDateTime.now())
                .set(SysNoticeDO::getIsRead, READ)
                .set(SysNoticeDO::getReadTime,
                        notice.getReadTime() == null ? LocalDateTime.now() : notice.getReadTime())
                .eq(SysNoticeDO::getNoticeId, id)) == 0) {
            throw new ServiceException(ErrorCode.NOTICE_NOT_EXISTS);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead() {
        Long userId = LoginUserHolder.getUserId();
        if (userId == null) {
            return;
        }
        sysNoticeMapper.update(null, Wrappers.<SysNoticeDO>lambdaUpdate()
                .set(SysNoticeDO::getIsRead, READ)
                .set(SysNoticeDO::getReadTime, LocalDateTime.now())
                .eq(SysNoticeDO::getIsRead, 0)
                .and(w -> w.eq(SysNoticeDO::getReceiverId, userId)
                        .or()
                        .isNull(SysNoticeDO::getReceiverId)));
    }

    @Override
    public long unreadCount() {
        Long userId = LoginUserHolder.getUserId();
        if (userId == null) {
            return 0L;
        }
        Long count = sysNoticeMapper.selectCount(Wrappers.<SysNoticeDO>lambdaQuery()
                .eq(SysNoticeDO::getIsRead, 0)
                .and(w -> w.eq(SysNoticeDO::getReceiverId, userId)
                        .or()
                        .isNull(SysNoticeDO::getReceiverId)));
        return count == null ? 0L : count;
    }

    // ------------------------------------------------------------------
    //  公告/通知管理端（管理员）
    // ------------------------------------------------------------------

    @Override
    public PageResult<NoticeRespVO> managePage(NoticePageReqVO reqVO) {
        LambdaQueryWrapper<SysNoticeDO> wrapper = Wrappers.lambdaQuery();
        wrapper.in(SysNoticeDO::getNoticeType, MANAGE_TYPE_IDS);
        applyFilters(wrapper, reqVO);
        wrapper.orderByDesc(SysNoticeDO::getSendTime).orderByDesc(SysNoticeDO::getNoticeId);
        Page<SysNoticeDO> page = sysNoticeMapper.selectPage(reqVO, wrapper);
        return toRespPage(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(NoticeSaveReqVO reqVO) {
        SysNoticeDO notice = new SysNoticeDO();
        notice.setTitle(reqVO.getTitle().trim());
        notice.setContent(reqVO.getContent());
        notice.setNoticeType(reqVO.getNoticeType());
        notice.setReceiverId(reqVO.getReceiverId());
        notice.setBizType(reqVO.getBizType());
        notice.setBizId(reqVO.getBizId());
        notice.setIsRead(0);
        notice.setSendTime(LocalDateTime.now());
        sysNoticeMapper.insert(notice);
        log.info("[消息通知] 新增 noticeId={} 类型={} 接收人={}", notice.getNoticeId(),
                notice.getNoticeType(), notice.getReceiverId());
        return notice.getNoticeId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, NoticeSaveReqVO reqVO) {
        SysNoticeDO notice = getManaged(id);
        SysNoticeDO update = new SysNoticeDO();
        update.setNoticeId(id);
        update.setTitle(reqVO.getTitle().trim());
        update.setContent(reqVO.getContent());
        update.setNoticeType(reqVO.getNoticeType());
        update.setReceiverId(reqVO.getReceiverId());
        // 改了范围/类型，已读态应重置，避免「旧读者已读、新读者看不到」的错位
        if (!Objects.equals(notice.getReceiverId(), reqVO.getReceiverId())
                || !Objects.equals(notice.getNoticeType(), reqVO.getNoticeType())) {
            update.setIsRead(0);
            update.setReadTime(null);
        }
        update.setBizType(reqVO.getBizType());
        update.setBizId(reqVO.getBizId());
        sysNoticeMapper.updateById(update);
        log.info("[消息通知] 修改 noticeId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getManaged(id);
        sysNoticeMapper.deleteById(id);
        log.info("[消息通知] 删除 noticeId={}", id);
    }

    // ------------------------------------------------------------------
    //  内部方法
    // ------------------------------------------------------------------

    /** 我的消息查询条件：接收人 = 我 OR 全员；再叠加筛选与排序 */
    private LambdaQueryWrapper<SysNoticeDO> selfWrapped(NoticePageReqVO reqVO, Long userId) {
        LambdaQueryWrapper<SysNoticeDO> wrapper = Wrappers.lambdaQuery();
        wrapper.and(w -> w.eq(SysNoticeDO::getReceiverId, userId)
                .or()
                .isNull(SysNoticeDO::getReceiverId));
        applyFilters(wrapper, reqVO);
        wrapper.orderByDesc(SysNoticeDO::getSendTime).orderByDesc(SysNoticeDO::getNoticeId);
        return wrapper;
    }

    private void applyFilters(LambdaQueryWrapper<SysNoticeDO> wrapper, NoticePageReqVO reqVO) {
        if (StringUtils.hasText(reqVO.getKeyword())) {
            wrapper.like(SysNoticeDO::getTitle, reqVO.getKeyword().trim());
        }
        if (reqVO.getNoticeType() != null) {
            wrapper.eq(SysNoticeDO::getNoticeType, reqVO.getNoticeType());
        }
        if (reqVO.getIsRead() != null) {
            wrapper.eq(SysNoticeDO::getIsRead, reqVO.getIsRead());
        }
    }

    /** 我的消息范围内取一条（receiver_id = 我 OR NULL + 未删除） */
    private SysNoticeDO getOwned(Long id) {
        Long userId = LoginUserHolder.getUserId();
        SysNoticeDO notice = sysNoticeMapper.selectById(id);
        if (notice == null) {
            throw new ServiceException(ErrorCode.NOTICE_NOT_EXISTS);
        }
        boolean mine = Objects.equals(notice.getReceiverId(), userId) || notice.getReceiverId() == null;
        if (!mine) {
            // 用自己的错误码但语义是「不是你的消息」，避免泄露别人消息是否存在
            throw new ServiceException(ErrorCode.NOTICE_NOT_EXISTS);
        }
        return notice;
    }

    /** 管理端取一条：限定 notice_type IN (2,3,4) */
    private SysNoticeDO getManaged(Long id) {
        SysNoticeDO notice = sysNoticeMapper.selectById(id);
        if (notice == null || !MANAGE_TYPE_IDS.contains(notice.getNoticeType())) {
            throw new ServiceException(ErrorCode.NOTICE_NOT_EXISTS);
        }
        return notice;
    }

    private PageResult<NoticeRespVO> toRespPage(Page<SysNoticeDO> page) {
        Map<Long, String> receiverNames = receiverNames(page.getRecords());
        List<NoticeRespVO> list = page.getRecords().stream()
                .map(n -> toRespVO(n, receiverNames.get(n.getReceiverId())))
                .collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    private NoticeRespVO toRespVO(SysNoticeDO n, String receiverName) {
        NoticeRespVO vo = new NoticeRespVO();
        vo.setId(n.getNoticeId());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setNoticeType(n.getNoticeType());
        vo.setType(typeOf(n.getNoticeType()));
        vo.setTypeName(typeNameOf(n.getNoticeType()));
        vo.setBizType(n.getBizType());
        vo.setBizId(n.getBizId());
        vo.setReceiverId(n.getReceiverId());
        vo.setReceiverName(receiverName);
        vo.setIsRead(n.getIsRead());
        vo.setHandleStatus(n.getHandleStatus());
        vo.setSendTime(n.getSendTime());
        return vo;
    }

    private String typeOf(Integer noticeType) {
        if (noticeType == null) {
            return "notice";
        }
        switch (noticeType) {
            case 1:
                return "approval";
            case 3:
                return "announcement";
            default:
                return "notice";
        }
    }

    private String typeNameOf(Integer noticeType) {
        if (noticeType == null) {
            return "通知";
        }
        switch (noticeType) {
            case 1:
                return "审批";
            case 2:
                return "公海回收";
            case 3:
                return "公告";
            case 4:
                return "任务提醒";
            default:
                return "通知";
        }
    }

    /** 批量查接收人姓名（仅管理端需要展示；「我的消息」里接收人常是本人） */
    private Map<Long, String> receiverNames(List<SysNoticeDO> notices) {
        List<Long> ids = notices.stream()
                .map(SysNoticeDO::getReceiverId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysUserMapper.selectList(SysUserDO::getUserId, ids).stream()
                .filter(u -> u.getUserId() != null && u.getRealName() != null)
                .collect(Collectors.toMap(SysUserDO::getUserId, SysUserDO::getRealName, (a, b) -> a));
    }
}
