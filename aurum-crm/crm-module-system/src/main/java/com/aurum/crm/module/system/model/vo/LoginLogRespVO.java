package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 登录日志响应 VO。
 *
 * <p><b>8 个字段与前端 {@code views/system/log/login.vue} 的 {@code columns} 严格一一对应</b>
 * （{@code prop} 名必须完全一致）。注意前端的字段名与表列名<b>并不相同</b>，
 * 映射集中在 {@code LoginLogServiceImpl#toRespVO}，不要为了「跟表对齐」而改这里的名字。
 *
 * <p>⚠️ {@code login_time} 之类的表列名在这里叫 {@code loginTime}，而 {@code user_name}
 * 对应的是 {@code sys_user.real_name}（跨表补的），别误以为是日志表里的列。
 */
@Data
@Schema(description = "登录日志")
public class LoginLogRespVO {

    /**
     * 登陆人 —— 跨表取 {@code sys_user.real_name}；账号不存在（登录失败且用户名查不到）时为空。
     *
     * <p>⚠️ 本字段与下面的 {@link #username} 的访问器<b>必须手写</b>，原因见 {@link #username}。
     */
    @Schema(description = "登陆人（真实姓名）", example = "超级管理员")
    private String userName;

    /**
     * 工号 / 登陆账号。
     *
     * <p>⚠️⚠️ <b>本类四个访问器（{@code getUserName} / {@code setUserName} /
     * {@code getUsername} / {@code setUsername}）全部手写，不能交给 Lombok。</b>
     *
     * <p>原因：Lombok 在判断「这个方法是不是已经有了」时是<b>大小写不敏感</b>的，
     * 而 {@code userName} 与 {@code username} 生成的访问器只差一个字母的大小写
     * （{@code setUserName} vs {@code setUsername}），于是它认为两者是同一个方法，
     * <b>每对字段只生成一个</b>。表现为：编译时毫无错误，只在调用处炸成
     * 「找不到符号: 方法 setUsername(String)」，极难看出是 Lombok 干的。
     *
     * <p>已用最小复现验证过：单独一个 {@code username} 字段访问器正常；
     * 一旦与 {@code userName} 并存，就必定缺一个（而且缺哪个取决于书写顺序）。
     *
     * <p>字段名不能改：前端 {@code login.vue} 的列 prop 就叫 {@code username}
     * （{@code userName} 是「登陆人姓名」，{@code username} 是「工号」，是两个不同的东西）。
     * 手写之后 Lombok 会跳过这两个同名（忽略大小写）的方法，不会重复生成。
     */
    @Schema(description = "工号（登陆账号）", example = "admin")
    private String username;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 手机号码 —— 跨表取 {@code sys_user.phone}，并**脱敏**成 {@code 138****8880}。
     *
     * <p>日志页是管理员才能看的，但仍然脱敏：手机号属于个人信息，
     * 展示在列表里没有业务必要（与《04-权限设计文档》4.1 对 phone 的处理口径一致）。
     */
    @Schema(description = "手机号码（已脱敏）", example = "138****8880")
    private String phone;

    /** 登陆时间 */
    @Schema(description = "登陆时间")
    private LocalDateTime loginTime;

    /** 登录IP */
    @Schema(description = "IP地址", example = "192.168.1.10")
    private String ip;

    /** 浏览器 */
    @Schema(description = "浏览器", example = "Chrome 120")
    private String browser;

    /** 操作系统 */
    @Schema(description = "操作系统", example = "Windows 11")
    private String os;

    /**
     * 登陆时长（小时）= {@code (logout_time - login_time)}。
     *
     * <p>⚠️ <b>会大量为空</b>：{@code logout_time} 只在用户**主动点退出登录**时才回填；
     * 关浏览器、会话过期、密码重置导致的强制下线都不会写。
     * 前端按空值展示即可，这不是错误。
     *
     * <p>保留 1 位小数：整点取整会让「半小时的会话」显示成 0，看起来像没记录。
     */
    @Schema(description = "登陆时长（小时，保留 1 位小数）；未登出时为空", example = "2.5")
    private BigDecimal duration;
}
