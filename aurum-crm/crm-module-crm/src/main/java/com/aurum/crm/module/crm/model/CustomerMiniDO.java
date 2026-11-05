package com.aurum.crm.module.crm.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 客户精简投影，只取商机列表 / 选择器「客户」列展示所需的字段。
 *
 * <p>与 {@code SysUserMiniDO} 同一套做法：放在 crm 模块里仅用于跨表补名。
 * 不直接复用 {@code CustomerDO} 是为了让「商机列表要读客户哪几列」这件事显式可见 ——
 * 顺带也不会把客户的手机号、地址等敏感列读进内存。
 *
 * <p><b>注意</b>：本类不继承 {@code BaseDO}，因此不会自动追加 {@code del_flag = 0}。
 * 已逻辑删除的客户仍能被历史商机引用到（历史单据要能显示客户名），这正是我们要的行为。
 */
@Data
@TableName("crm_customer")
public class CustomerMiniDO implements Serializable {

    /** 客户ID */
    @TableId
    private Long customerId;

    /** 客户名称 */
    private String customerName;
}
