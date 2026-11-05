package com.aurum.crm.module.system.dal.dataobject;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商机明细只读投影，<b>只读</b> {@code crm_opportunity_item}。
 *
 * <p>用途与理由同 {@link CrmProductMiniDO}：商机明细的 {@code unit_id} 是引用，
 * 删掉单位会让商机详情明细行的「单位」列变成 {@code --}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_opportunity_item")
public class CrmOpportunityItemMiniDO extends BaseDO {

    /** 明细ID */
    @TableId
    private Long itemId;

    /** 计量单位ID */
    private Long unitId;
}
