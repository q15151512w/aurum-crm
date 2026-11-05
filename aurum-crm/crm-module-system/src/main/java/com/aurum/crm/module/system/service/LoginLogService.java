package com.aurum.crm.module.system.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.model.dto.LoginLogPageReqVO;
import com.aurum.crm.module.system.model.vo.LoginLogRespVO;

import java.time.LocalDateTime;

/**
 * 登录日志服务。
 *
 * <p>本接口分两组职责，别混在一起看：
 * <ol>
 *     <li><b>查询</b>（给前端「登陆日志」页用）—— {@link #pageQuery}</li>
 *     <li><b>写入</b>（登录/登出时被 {@code AuthServiceImpl} 调用）—— 其余 4 个方法</li>
 * </ol>
 *
 * <p><b>写入侧为什么要单独暴露这几个方法</b>：日志的写入与认证流程耦合在各种失败分支里
 * （验证码错、密码错、账号禁用…），直接在 {@code AuthServiceImpl} 里拼 DO 会让本已很长的
 * 登录方法更难读；集中在这里，{@code AuthServiceImpl} 只需要「告诉我在什么时机记什么」。
 *
 * <p><b>本 Service 不抛业务异常</b>：日志写失败绝不能影响登录本身，
 * 所有写入方法内部自行消化异常（见实现类的说明）。
 */
public interface LoginLogService {

    /**
     * 分页查询。排序固定 {@code login_time DESC, log_id DESC}。
     *
     * <p>{@code keyword} 同时匹配 登陆人（real_name） / 工号（username） / IP。
     * 返回前会跨表批量补 {@code realName} 与脱敏后的 {@code phone}，并算出 {@code duration}。
     */
    PageResult<LoginLogRespVO> pageQuery(LoginLogPageReqVO reqVO);

    /**
     * 导出登录日志为 xlsx（上传 OSS，返回下载 URL）。
     *
     * <p>过滤条件与 {@link #pageQuery} 一致（keyword），但不翻页，导出全部匹配记录。
     */
    String export(LoginLogPageReqVO reqVO);

    /**
     * 导出登录日志为 SpreadsheetML（.xml，上传 OSS，返回下载 URL）。
     */
    String exportXml(LoginLogPageReqVO reqVO);

    /**
     * 记录一次<b>登录成功</b>，返回新建日志的 {@code logId}。
     *
     * <p>调用方（{@code AuthServiceImpl.doLogin}）必须把返回的 id 存进 Sa-Token Session，
     * 登出时才能回填 {@code logout_time} —— 这是「登陆时长」唯一的关联依据。
     *
     * @return 新日志的 logId；<b>写库失败时返回 null</b>（登录流程不应因此中断）
     */
    Long recordLoginSuccess(Long userId, String username, String clientIp,
                            String browser, String os, Integer loginType);

    /**
     * 记录一次<b>登录失败</b>。
     *
     * <p>⚠️ {@code msg} 由调用方传入，但调用方<b>必须</b>遵守防账号枚举的口径：
     * 「用户不存在」与「密码错误」要写成同一句话（见 {@code AuthServiceImpl} 的说明）。
     *
     * @param userId 失败时通常为 null（账号可能压根不存在）
     */
    void recordLoginFailure(String username, Long userId, String clientIp,
                            String browser, String os, Integer loginType, String msg);

    /**
     * 回填登出时间。
     *
     * <p>由 {@code AuthServiceImpl.logout()} 在 {@code StpUtil.logout()} <b>之前</b>调用 ——
     * 注销之后 Session 就取不到了。
     *
     * @param logId 登录成功时存进 Session 的 id；为 null 时直接返回（老会话没有这个值，属正常）
     */
    void recordLogout(Long logId, LocalDateTime logoutTime);
}
