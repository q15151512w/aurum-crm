package com.aurum.crm.module.system.dal.dataobject;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 按流水依据分组保存的编号计数器。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_numbering_counter")
public class NumberingRuleCounterDO extends BaseDO {
    @TableId private Long counterId;
    private Long ruleId;
    private String basisKey;
    private Long currentSeq;
}
