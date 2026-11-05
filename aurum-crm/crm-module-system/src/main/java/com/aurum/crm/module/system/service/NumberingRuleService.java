package com.aurum.crm.module.system.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.model.dto.NumberingRulePageReqVO;
import com.aurum.crm.module.system.model.dto.NumberingRuleSaveReqVO;
import com.aurum.crm.module.system.model.vo.NumberingRuleRespVO;

import java.util.Map;

public interface NumberingRuleService {
    PageResult<NumberingRuleRespVO> page(NumberingRulePageReqVO req);
    Long create(NumberingRuleSaveReqVO req);
    void update(Long id, NumberingRuleSaveReqVO req);
    void delete(Long id);
    String next(String ruleCode);
    String next(String ruleCode, Map<String, Object> context);
}
