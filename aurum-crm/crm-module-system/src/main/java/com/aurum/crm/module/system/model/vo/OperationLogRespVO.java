package com.aurum.crm.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志响应 VO。
 *
 * <p><b>9 个字段与前端 {@code views/system/log/operation.vue} 的 {@code columns}
 * 严格一一对应</b>。前端的字段名与表列名不同（{@code userName}←{@code oper_name}、
 * {@code module}←{@code title}、{@code action}←{@code business_type}…），
 * 映射集中在 {@code OperationLogServiceImpl#toRespVO}。
 *
 * <p>⚠️ 本类<b>没有</b> {@code username} 字段，所以不存在
 * {@code LoginLogRespVO} 那个「Lombok 大小写不敏感导致访问器被跳过」的坑。
 * 将来若给本类加 {@code username}，请先读 {@code LoginLogRespVO#username} 的注释。
 */
@Data
@Schema(description = "操作日志")
public class OperationLogRespVO {

    /** 操作人 —— 表列 {@code oper_name} */
    @Schema(description = "操作人", example = "超级管理员")
    private String userName;

    /** 模块 —— 表列 {@code title}（取 Controller 的 {@code @Tag(name)}） */
    @Schema(description = "模块", example = "客户管理")
    private String module;

    /**
     * 操作 —— 表列 {@code business_type}，**已转中文标签**（新增 / 修改 / 删除 / …）。
     *
     * <p>按《05-接口文档》1.8 的状态机枚举口径，由后端转标签下发。
     */
    @Schema(description = "操作类型（中文标签）", example = "新增")
    private String action;

    /** 方法 —— 表列 {@code method}（{@code 类名.方法()}） */
    @Schema(description = "方法", example = "CustomerController.create()")
    private String method;

    /** 请求参数（JSON 字符串，**已脱敏**：密码等字段是 {@code ****}） */
    @Schema(description = "请求参数（已脱敏）", example = "{\"customerName\":\"某某公司\"}")
    private String params;

    /** IP地址 —— 表列 {@code oper_ip} */
    @Schema(description = "IP地址", example = "192.168.1.10")
    private String ip;

    /** 耗时(ms) —— 表列 {@code cost_time} */
    @Schema(description = "耗时(毫秒)", example = "35")
    private Long costMs;

    /**
     * 状态 —— 表列 {@code status}，**已转中文**「成功」/「失败」。
     *
     * <p>⚠️ 必须是中文：前端 {@code #cell-status} 插槽靠 {@code value === '成功'} 判标签颜色，
     * 给数字会让整列渲染成失败的红色。
     */
    @Schema(description = "状态（中文）", example = "成功")
    private String status;

    /** 操作时间 —— 表列 {@code oper_time} */
    @Schema(description = "操作时间")
    private LocalDateTime operateTime;
}
