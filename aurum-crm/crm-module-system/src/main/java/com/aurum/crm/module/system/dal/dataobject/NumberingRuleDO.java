package com.aurum.crm.module.system.dal.dataobject;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_numbering_rule")
public class NumberingRuleDO extends BaseDO {
    @TableId private Long ruleId;
    private String ruleCode;
    private String ruleName;
    private String prefix;
    private String dateFormat;
    private Integer seqLength;
    private Integer resetCycle;
    private Long currentSeq;
    private String currentPeriod;
    private Integer enabled;
    private String remark;
}
