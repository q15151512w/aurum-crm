package com.aurum.crm.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.model.dto.NoticePageReqVO;
import com.aurum.crm.module.system.model.dto.NoticeSaveReqVO;
import com.aurum.crm.module.system.model.vo.NoticeRespVO;
import com.aurum.crm.module.system.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息通知 Controller，对应「消息通知」页与「公告/通知管理」两个入口。
 *
 * <p>路由与契约见《05-接口文档》3.6。两个入口共用一张 {@code sys_notice}，用子路径区分，
 * 避免同一 {@code GET /system/notice} 因权限不同产生语义冲突：
 * <ul>
 *     <li><b>我的消息</b>（{@code /system/notice}，任意登录用户）：只读自己的
 *         （{@code receiver_id = 我 OR IS NULL}），可标记已读。无 {@code @SaCheckPermission}。</li>
 *     <li><b>公告/通知管理</b>（{@code /system/notice/manage}，管理员）：对
 *         {@code notice_type IN (2,3,4)} 的行增删改，带 {@code system:notice:*}</li>
 * </ul>
 */
@Tag(name = "消息通知")
@RestController
@RequestMapping("/system/notice")
@Validated
public class NoticeController {

    @Resource
    private NoticeService noticeService;

    // ------------------------------------------------------------------
    //  我的消息（任意登录用户）
    // ------------------------------------------------------------------

    @Operation(summary = "我的消息分页（服务端强制注入 receiver_id = 我 OR NULL；可按 noticeType/isRead/keyword 过滤）")
    @GetMapping
    public CommonResult<PageResult<NoticeRespVO>> page(NoticePageReqVO reqVO) {
        return CommonResult.success(noticeService.pageQuery(reqVO));
    }

    @Operation(summary = "标记单条已读")
    @PutMapping("/{id}/read")
    public CommonResult<Boolean> markRead(@PathVariable("id") Long id) {
        noticeService.markRead(id);
        return CommonResult.success(true);
    }

    @Operation(summary = "标记单条已处理（已读态一并置 1）")
    @PutMapping("/{id}/handle")
    public CommonResult<Boolean> markHandled(@PathVariable("id") Long id) {
        noticeService.markHandled(id);
        return CommonResult.success(true);
    }

    @Operation(summary = "一键全部已读")
    @PutMapping("/read-all")
    public CommonResult<Boolean> markAllRead() {
        noticeService.markAllRead();
        return CommonResult.success(true);
    }

    @Operation(summary = "未读数（顶栏红点用）")
    @GetMapping("/unread-count")
    public CommonResult<Long> unreadCount() {
        return CommonResult.success(noticeService.unreadCount());
    }

    // ------------------------------------------------------------------
    //  公告/通知管理端（管理员）
    // ------------------------------------------------------------------

    @SaCheckPermission("system:notice:list")
    @Operation(summary = "公告/通知管理分页（限定 notice_type IN (2,3,4)，含接收人姓名）")
    @GetMapping("/manage")
    public CommonResult<PageResult<NoticeRespVO>> managePage(NoticePageReqVO reqVO) {
        return CommonResult.success(noticeService.managePage(reqVO));
    }

    @SaCheckPermission("system:notice:create")
    @Operation(summary = "新增公告/通知（receiverId 空 = 全员公告，否则定向通知）")
    @PostMapping("/manage")
    public CommonResult<Long> create(@Valid @RequestBody NoticeSaveReqVO reqVO) {
        return CommonResult.success(noticeService.create(reqVO));
    }

    @SaCheckPermission("system:notice:update")
    @Operation(summary = "修改公告/通知")
    @PutMapping("/manage/{id}")
    public CommonResult<Boolean> update(@PathVariable("id") Long id,
                                        @Valid @RequestBody NoticeSaveReqVO reqVO) {
        noticeService.update(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("system:notice:delete")
    @Operation(summary = "删除公告/通知（逻辑删除）")
    @DeleteMapping("/manage/{id}")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        noticeService.delete(id);
        return CommonResult.success(true);
    }
}
