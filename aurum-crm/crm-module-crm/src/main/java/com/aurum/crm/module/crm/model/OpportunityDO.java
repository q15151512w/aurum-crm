package com.aurum.crm.module.crm.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 商机 DO，对应表 {@code crm_opportunity}。
 *
 * <p><b>字段名与数据库列严格一一对应</b>：DO 只负责搬运，对外叫什么名字由
 * {@link com.aurum.crm.module.crm.model.convert.OpportunityConvert} 决定
 * （对外一律用 {@code crm-ui} 前端的叫法，对照表见 {@code docs/09-商机模块字段契约.md}）。
 *
 * <p><b>注意</b>：本类<b>不能</b>声明 {@code isActive} 字段 ——
 * 它是数据库生成列（{@code IF(del_flag = 0, 1, NULL)}），只读，
 * 写进去会报 {@code The value specified for generated column ... is not allowed}。
 * 它的作用是配合 {@code uk_opportunity_no(opportunity_no, is_active)}，
 * 让逻辑删除后的商机编号可以被重新使用（唯一索引中 NULL 之间不冲突）。
 *
 * <p><b>数据权限</b>：{@code owner_user_id} 是 {@code NOT NULL}（商机没有公海概念，
 * 见《04-权限设计文档》3.4），所以数据权限条件里没有
 * {@code owner_user_id IS NULL} 那个「公海可见」分支。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_opportunity")
public class OpportunityDO extends BaseDO implements Serializable {

    /** 商机ID */
    @TableId
    private Long opportunityId;

    /**
     * 商机编号，业务唯一键，由服务端生成（{@code "SJ" + 雪花ID}）。
     *
     * <p>与客户模块（{@code KH}）、产品模块（{@code CP}）同一套占位做法；
     * 待 system 模块的编号规则引擎落地后，只需替换
     * {@code OpportunityServiceImpl#buildOpportunityNo()} 一处。
     */
    private String opportunityNo;

    /** 商机名称 */
    private String opportunityName;

    /** 关联客户ID（crm_customer.customer_id） */
    private Long customerId;

    /** 关联联系人ID（crm_contact.contact_id，可空；前端商机表单暂无这一项） */
    private Long contactId;

    /** 预计金额 */
    private BigDecimal amount;

    /** 销售阶段（1初步接洽 2需求确认 3方案报价 4商务谈判 5赢单 6输单） */
    private Integer stage;

    /**
     * 整单折扣(%)，0–100。
     *
     * <p>对应前端表单的 {@code discount} 与列表的「整单折扣(%)」列；
     * 对外的「整单最终价」由它派生（{@code amount × (1 − discount_rate / 100)}），**不落库**。
     *
     * <p>⚠️ 这一列原来是 {@code win_rate}（赢单概率），后来被改名成 {@code discount_rate}。
     * 真库上它是**可空**的（{@code NULL DEFAULT NULL}），而 {@code sql/crm.sql} 里写的是
     * {@code NOT NULL DEFAULT 0.00} —— 代码**写时永不写 NULL、读时把 NULL 归一成 0**，
     * 所以两种建表都能跑。
     */
    private BigDecimal discountRate;

    /** 商机来源（字典 crm_source，可空；前端商机表单暂无这一项） */
    private Integer source;

    /** 归属人ID（必填，商机没有公海） */
    private Long ownerUserId;

    /** 归属部门ID，跟着**负责人**所在部门走（不是创建人） */
    private Long deptId;

    /** 预计成交日期 */
    private LocalDate expectedCloseDate;

    /** 实际成交日期，赢单时自动落当天 */
    private LocalDate actualCloseDate;

    /** 下次联系时间（前端列表「下次联系时间」列，标红提醒已过期） */
    private LocalDate nextContactTime;

    /** 备注 */
    private String remark;
}
