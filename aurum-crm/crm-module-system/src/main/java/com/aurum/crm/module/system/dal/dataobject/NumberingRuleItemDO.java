package com.aurum.crm.module.system.dal.dataobject;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 编号规则中的一个可排序节点。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_numbering_rule_item")
public class NumberingRuleItemDO extends BaseDO {
    @TableId private Long itemId;
    private Long ruleId;
    private String itemType;
    private String itemValue;
    private Integer itemLength;
    private Integer sort;
    private Integer sequenceBasis;
}
