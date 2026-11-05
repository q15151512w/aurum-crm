package com.aurum.crm.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志 DO，对应表 {@code sys_login_log}（《02-建表文档》15 节）。
 *
 * <p><b>为什么不能继承 {@code BaseDO}</b>：{@code BaseDO} 带着 {@code @TableLogic} 的
 * {@code delFlag}，而本表是**只追加流水表**，库里根本没有
 * {@code del_flag} / {@code create_by} / {@code update_by} / {@code update_time} 四列。
 * 继承了会让 MyBatis-Plus 生成的每一条 SELECT 都追加 {@code del_flag = 0}，
 * 直接报 {@code Unknown column 'del_flag' in 'where clause'}。
 * 写法与 {@code OpportunityStageLogDO} / {@code OwnerChangeLogDO} 一致：
 * {@code implements Serializable} + 手写 {@code @TableId}。
 *
 * <p><b>字段名刻意避开 {@code createTime} / {@code updateTime} / {@code createBy} / {@code updateBy} / {@code delFlag}</b>：
 * {@code DefaultDBFieldHandler.insertFill} 是按「DO 里有没有这个 setter」来填的，
 * 一旦撞名就会被**无条件填成当前时间/当前登录人**，覆盖掉 DB 的
 * {@code DEFAULT CURRENT_TIMESTAMP}。本类用 {@code loginTime} / {@code logoutTime}，天然安全。
 *
 * <p><b>{@code @TableId} 不要加 {@code type} 属性</b>：全局配置已是 {@code ASSIGN_ID}（雪花），
 * 加了会被覆盖成手动赋值，插入时报 id 为空。
 *
 * <p><b>{@code location}（登录地点）恒为 NULL</b>：需要 IP 归属地服务，项目没有接入，
 * 保留该字段只是为了与 DDL 对齐、将来要接时不改表。
 */
@Data
@TableName("sys_login_log")
public class SysLoginLogDO implements Serializable {

    /** 日志ID */
    @TableId
    private Long logId;

    /** 登录账号。⚠️ 邮箱验证码登录时入参只有邮箱，这里可能为空 */
    private String username;

    /**
     * 用户ID。
     *
     * <p>⚠️ <b>登录失败时恒为 NULL</b>：账号可能压根不存在，
     * 失败路径上拿不到 userId（DDL 允许 NULL）。
     */
    private Long userId;

    /** 登录IP */
    private String ip;

    /** 登录地点（恒为 NULL，见类注释） */
    private String location;

    /** 浏览器（含版本号，如 {@code Chrome 120}） */
    private String browser;

    /** 操作系统（如 {@code Windows 11}） */
    private String os;

    /** 登录方式：1 密码 / 2 邮箱验证码（原本是短信验证码，码值沿用未变） */
    private Integer loginType;

    /**
     * 登录状态：<b>0 失败 / 1 成功</b>。
     *
     * <p>⚠️ 与 {@code sys_user.status}（1 正常 / 2 禁用 / 3 离职）语义**完全不同**，别混用。
     */
    private Integer status;

    /** 提示消息（失败原因）。⚠️ 见 {@code AuthServiceImpl}：失败文案必须统一，防止账号枚举 */
    private String msg;

    /** 登录时间 */
    private LocalDateTime loginTime;

    /**
     * 登出时间。
     *
     * <p>⚠️ <b>会大片为 NULL</b>：用户直接关浏览器不触发登出；
     * {@code forgotPassword} / {@code changePassword} 走的是 {@code StpUtil.kickout} 而不是
     * {@code logout()}，会话被踢下线同理。前端的「登陆时长」对这些记录为空，是预期行为。
     */
    private LocalDateTime logoutTime;
}
