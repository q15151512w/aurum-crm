package com.aurum.crm.module.system.dal.dataobject;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报价单明细只读投影，<b>只读</b> {@code crm_quotation_item}。
 *
 * <p>用途与理由同 {@link CrmProductMiniDO}：报价单明细的 {@code unit_id} 是<b>引用</b>而不是快照，
 * 删掉单位会让历史报价单明细行的「单位」列变成 {@code --}（见 {@code ItemSupport.loadUnitNames()}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_quotation_item")
public class CrmQuotationItemMiniDO extends BaseDO {

    /** 明细ID */
    @TableId
    private Long itemId;

    /** 计量单位ID */
    private Long unitId;
}
