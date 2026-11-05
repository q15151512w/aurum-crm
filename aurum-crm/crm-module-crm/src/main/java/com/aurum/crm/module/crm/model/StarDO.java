package com.aurum.crm.module.crm.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 关注记录 DO，对应表 {@code crm_star}。
 *
 * <p><b>通用表</b>：靠 {@code biz_type + biz_id} 指向任意业务对象，
 * 线索 / 客户 / 商机 / 报价单 / 合同 / 回款 / 发票共用一张，见 {@link StarBizType}。
 *
 * <p>唯一键 {@code uk_biz_user (biz_type, biz_id, user_id, is_active)} 保证
 * 「同一人对同一对象只有一条有效关注」。取消关注走逻辑删除（{@code del_flag}），
 * {@code is_active} 随之变 NULL，因此重新关注能插入新行而不会撞唯一键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_star")
public class StarDO extends BaseDO implements Serializable {

    /** 关注ID */
    @TableId
    private Long starId;

    /** 业务类型（见 {@link StarBizType}：1线索 2客户 3商机 4报价单 5合同 6回款 7发票） */
    private Integer bizType;

    /** 业务ID（线索ID / 客户ID / 商机ID / …） */
    private Long bizId;

    /** 关注人ID */
    private Long userId;
}
