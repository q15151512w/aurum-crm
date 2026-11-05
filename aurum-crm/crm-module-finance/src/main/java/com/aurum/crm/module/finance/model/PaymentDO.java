package com.aurum.crm.module.finance.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 回款 DO，对应表 {@code fin_payment}。
 *
 * <p>归属人 {@code owner_user_id} 非空（回款是有人认领的单据），
 * 数据权限由 {@code DataPermissionInnerInterceptor} 统一改写（与客户模块同法）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_payment")
public class PaymentDO extends BaseDO {

    /** 回款ID */
    @TableId
    private Long paymentId;

    /** 回款编号 */
    private String paymentNo;

    /** 关联合同ID */
    private Long contractId;

    /** 关联客户ID */
    private Long customerId;

    /** 归属人ID */
    private Long ownerUserId;

    /** 归属部门ID */
    private Long deptId;

    /** 回款金额 */
    private BigDecimal amount;

    /** 回款日期 */
    private LocalDate paymentDate;

    /** 回款方式（1银行转账 2现金 3支票 4在线支付 5其他） */
    private Integer paymentMethod;

    /** 收款账户名 */
    private String accountName;

    /** 收款账号 */
    private String accountNo;

    /** 银行流水号 */
    private String serialNo;

    /** 审核状态（0待提审 1审批中 2已通过 3已驳回） */
    private Integer auditStatus;

    /** 单据状态（0待确认 1已确认 2已作废） */
    private Integer status;

    /** 备注 */
    private String remark;
}
