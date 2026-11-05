package com.aurum.crm.module.system.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.model.dto.UserPageReqVO;
import com.aurum.crm.module.system.model.dto.UserSaveReqVO;
import com.aurum.crm.module.system.model.dto.UserStatusReqVO;
import com.aurum.crm.module.system.model.dto.UserSuperiorReqVO;
import com.aurum.crm.module.system.model.vo.UserResetPasswordRespVO;
import com.aurum.crm.module.system.model.vo.UserRespVO;
import com.aurum.crm.module.system.model.vo.UserSimpleRespVO;

import java.util.List;

/**
 * 人员管理（接口文档 3.1）。
 *
 * <p>这一套接口打通的是「注册 → 管理员分配角色 → 激活 → 能登录」这条链路，
 * 其中 {@link #changeStatus} 的「激活前必须有角色」是「有账号必有角色」的兜底。
 */
public interface UserService {

    PageResult<UserRespVO> pageQuery(UserPageReqVO reqVO);

    UserRespVO get(Long id);

    /** @return 新建用户的 ID */
    Long create(UserSaveReqVO reqVO);

    void update(Long id, UserSaveReqVO reqVO);

    /** 删除人员，同时清掉它的角色关联 */
    void delete(Long id);

    /**
     * 激活 / 禁用 / 操作离职。
     *
     * @throws com.aurum.crm.common.exception.ServiceException 激活时该用户没有任何角色（1106）
     */
    void changeStatus(Long id, UserStatusReqVO reqVO);

    /** 设置直属上级。传 null 表示清空 */
    void setSuperior(Long id, UserSuperiorReqVO reqVO);

    /** 重置密码，返回后端生成的新密码 */
    UserResetPasswordRespVO resetPassword(Long id);

    /** 人员选择器数据源（分页版，供人员管理列表等需要翻页的地方用） */
    List<UserSimpleRespVO> simpleList(UserPageReqVO reqVO);

    /**
     * 人员选择器数据源（<b>不分页</b>，供各业务表单的「负责人 / 签约人」下拉用）。
     *
     * <p><b>为什么必须单开一个</b>：{@link #simpleList} 走的是 {@code selectPage}，
     * 而 {@code PageParam} 的默认 {@code pageSize} 是 <b>10</b>。前端下拉请求
     * 不带分页参数，于是只拿到前 10 个用户 —— 排在后面的销售在下拉里
     * <b>根本不存在</b>，表现为「选不了自己 / 选不了某个人」，且<b>没有任何报错</b>。
     *
     * <p>下拉本来就需要全量（前端还要在本地做关键字过滤），分页在这里没有意义。
     * 与 {@code /crm/customer/owner-options} 的既有做法保持一致。
     */
    List<UserSimpleRespVO> options(UserPageReqVO reqVO);

    /**
     * 导出人员 Excel（接口文档 3.1 {@code GET /system/user/export}）并上传 OSS。
     *
     * @return OSS 下载 URL（字符串）
     */
    String export(UserPageReqVO reqVO);

    /**
     * 导出人员 XML/SpreadsheetML（接口文档 3.1 {@code GET /system/user/export-xml}）并上传 OSS。
     *
     * @return OSS 下载 URL（字符串）
     */
    String exportXml(UserPageReqVO reqVO);
}
