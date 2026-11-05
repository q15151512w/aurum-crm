package com.aurum.crm.module.finance.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_numbering_rule")
public class SysNumberingRuleMiniDO {
    @TableId private Long ruleId;
    private String ruleCode;
    private String prefix;
    private String dateFormat;
    private Integer seqLength;
    private Integer resetCycle;
    private Long currentSeq;
    private String currentPeriod;
    private Integer enabled;
    @TableLogic private Integer delFlag;
}
