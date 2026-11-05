package com.aurum.crm.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志 DO，对应表 {@code sys_operation_log}（《02-建表文档》14 节）。
 *
 * <p><b>与 {@link SysLoginLogDO} 同款约束</b>：
 * <ul>
 *     <li><b>不能继承 {@code BaseDO}</b> —— 本表没有 {@code del_flag} / {@code create_by} /
 *         {@code update_by} / {@code update_time} 四列，继承了会让每条 SELECT 追加
 *         {@code del_flag = 0} 而报 {@code Unknown column}</li>
 *     <li>字段名<b>避开</b> {@code createTime} / {@code updateTime} / {@code createBy} /
 *         {@code updateBy} / {@code delFlag} —— {@code DefaultDBFieldHandler.insertFill}
 *         按「有没有这个 setter」来填，撞名会被无条件覆盖成本应用时间。
 *         业务时间列叫 {@code operTime}，天然安全</li>
 *     <li>{@code @TableId} <b>不加 {@code type}</b> —— 全局已是 {@code ASSIGN_ID}（雪花）</li>
 * </ul>
 *
 * <p><b>本表只由 {@code OperationLogAspect} 写入</b>，没有任何接口能改或删 —— 审计的基本要求。
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLogDO implements Serializable {

    /** 日志ID */
    @TableId
    private Long logId;

    /** 模块标题（取 Controller 上的 {@code @Tag(name)}，或被 {@code @Log.title()} 覆盖） */
    private String title;

    /** 业务类型：0 其他 / 1 新增 / 2 修改 / 3 删除 / 4 查询 / 5 导出 / 6 导入 */
    private Integer businessType;

    /** 方法名称（{@code 类名.方法()}） */
    private String method;

    /** 请求方式（GET / POST / PUT / DELETE） */
    private String requestMethod;

    /** 操作类别：0 其他 / 1 后台用户 / 2 手机端用户 */
    private Integer operatorType;

    /** 操作人ID（未登录时为空，如登录接口本身） */
    private Long operatorId;

    /** 操作人名称 */
    private String operName;

    /** 操作人部门 */
    private String deptName;

    /** 请求URL */
    private String operUrl;

    /** 操作IP */
    private String operIp;

    /**
     * 请求参数（JSON）。
     *
     * <p>⚠️ <b>写入前必须脱敏</b>：登录/改密接口的参数里有明文密码。
     * 具体见 {@code OperationLogAspect#desensitize}。
     */
    private String operParam;

    /** 返回结果（JSON）。⚠️ 本项目<b>只在失败时</b>写这一列（成功不写，省体积也少泄露） */
    private String jsonResult;

    /** 操作状态：<b>0 失败 / 1 成功</b> */
    private Integer status;

    /** 错误消息（失败时写异常信息） */
    private String errorMsg;

    /** 操作时间 */
    private LocalDateTime operTime;

    /** 耗时（毫秒） */
    private Long costTime;
}
