package com.aurum.crm.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 调用日志 DO，对应表 {@code sys_ai_call_log}（《建表文档》21 节）。
 *
 * <p><b>为什么不能继承 {@code BaseDO}</b>：{@code BaseDO} 带着 {@code @TableLogic} 的
 * {@code delFlag}，而本表是**只追加流水表**，库里根本没有
 * {@code del_flag} / {@code create_by} / {@code update_by} / {@code update_time} 四列。
 * 继承了会让 MyBatis-Plus 生成的每一条 SELECT 都追加 {@code del_flag = 0}，
 * 直接报 {@code Unknown column 'del_flag' in 'where clause'}。
 * 写法与 {@code SysLoginLogDO} / {@code OwnerChangeLogDO} 一致：
 * {@code implements Serializable} + 手写 {@code @TableId}。
 *
 * <p>{@code createTime} 这个字段名**可以**用（与 {@code SysLoginLogDO} 的情况不同）：
 * 本表 DDL 里确实有 {@code create_time} 且就是「写入时刻」，
 * {@code DefaultDBFieldHandler.insertFill} 把它填成当前时间正是想要的行为。
 *
 * <p><b>{@code @TableId} 不要加 {@code type} 属性</b>：全局配置已是 {@code ASSIGN_ID}（雪花），
 * 加了会被覆盖成手动赋值，插入时报 id 为空。
 *
 * <p><b>{@code input_digest} / {@code output_digest} 是摘要不是全文</b>：
 * 表注释写明「不存全文，避免敏感数据落库」。写入前必须截断（见
 * {@code AiCallLogServiceImpl}），超了会直接 {@code Data too long}。
 */
@Data
@TableName("sys_ai_call_log")
public class AiCallLogDO implements Serializable {

    /** 日志ID */
    @TableId
    private Long logId;

    /** 应用场景。本功能固定 {@code QA}（问答）；同表还预留了 FOLLOW_SUMMARY 等场景 */
    private String scene;

    /** 模型名称，如 {@code deepseek-chat} */
    private String modelName;

    /** 关联业务类型。问答场景不绑定具体业务对象，恒为 null */
    private String bizType;

    /** 关联业务ID。同上，恒为 null */
    private Long bizId;

    /** 输入 Token 数 */
    private Integer promptTokens;

    /** 输出 Token 数 */
    private Integer completionTokens;

    /** 总 Token 数 */
    private Integer totalTokens;

    /**
     * 费用。
     *
     * <p>当前恒为 0：各模型单价不同且会调整，把价格表写进代码只会腐烂。
     * 要真正计费的话应该建一张价格配置表，按 model + 时段算 —— 属于后续的事。
     */
    private BigDecimal cost;

    /** 耗时（毫秒），从发起上游请求到流结束 */
    private Integer latencyMs;

    /** 状态（0失败 1成功） */
    private Integer status;

    /** 错误信息（失败时），同样截断到 500 字符 */
    private String errorMsg;

    /** 输入摘要 */
    private String inputDigest;

    /** 输出摘要 */
    private String outputDigest;

    /** 操作人ID。由调用方从请求线程显式传入，不在 Service 里读 ThreadLocal */
    private Long operatorId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
