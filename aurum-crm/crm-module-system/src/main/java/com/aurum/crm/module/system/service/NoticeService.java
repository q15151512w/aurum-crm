package com.aurum.crm.module.system.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.model.dto.NoticePageReqVO;
import com.aurum.crm.module.system.model.dto.NoticeSaveReqVO;
import com.aurum.crm.module.system.model.vo.NoticeRespVO;

/**
 * 消息通知服务（《05-接口文档》3.6 基础配置）。
 *
 * <p>两个入口，共借一张 {@code sys_notice} 表：
 * <ol>
 *     <li><b>我的消息</b>（面向所有登录用户）：{@link #pageQuery} 只读自己
 *         （{@code receiver_id = 我 OR IS NULL}）；{@link #markRead} / {@link #markAllRead} /
 *         {@link #unreadCount} 操作自己的消息。</li>
 *     <li><b>公告/通知管理</b>（管理员）：{@link #managePage} / {@link #create} /
 *         {@link #update} / {@link #delete} 维护 {@code notice_type IN (2,3,4)} 的行。
 *         公告（{@code notice_type=3}、{@code receiver_id=null}）广播给全员，
 *         通知（{@code receiver_id=具体人}）定向。</li>
 * </ol>
 *
 * <p>⚠️ {@code sys_notice} 不在数据权限白名单里，可见性由 {@code receiver_id} 决定，
 * 所以查询都显式带接收人条件（「我的消息」侧）或类型条件（「管理端」侧）。
 */
public interface NoticeService {

    /**
     * 我的消息分页。
     *
     * <p>服务端强制注入 {@code receiver_id = 当前登录人 OR receiver_id IS NULL}，
     * 前端/first 无论传不传 receiver 都只会看到自己的消息 + 全员公告。
     */
    PageResult<NoticeRespVO> pageQuery(NoticePageReqVO reqVO);

    /** 标记单条已读（只允许操作自己的消息；全员公告也视为「自己的」） */
    void markRead(Long id);

    /** 标记单条已处理（只允许操作自己的消息；处理时已读态一并置 1） */
    void markHandled(Long id);

    /** 一键全部已读（自己的消息范围内） */
    void markAllRead();

    /** 未读数（自己的消息范围内） */
    long unreadCount();

    /** 公告/通知管理分页（{@code notice_type IN (2,3,4)}，不受接收人限制） */
    PageResult<NoticeRespVO> managePage(NoticePageReqVO reqVO);

    /** 新增公告/通知，返回新通知 ID */
    Long create(NoticeSaveReqVO reqVO);

    /** 修改公告/通知（只允许改 {@code notice_type IN (2,3,4)} 的行） */
    void update(Long id, NoticeSaveReqVO reqVO);

    /** 逻辑删除公告/通知（只允许删 {@code notice_type IN (2,3,4)} 的行） */
    void delete(Long id);
}
