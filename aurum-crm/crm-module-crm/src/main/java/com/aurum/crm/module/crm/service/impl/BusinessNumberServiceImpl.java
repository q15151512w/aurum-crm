package com.aurum.crm.module.crm.service.impl;

import com.aurum.crm.module.crm.service.BusinessNumberService;
import com.aurum.crm.module.system.service.NumberingRuleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

/** CRM 业务编号适配器，实际生成统一由系统模块的编号规则引擎完成。 */
@Service
public class BusinessNumberServiceImpl implements BusinessNumberService {
    @Resource private NumberingRuleService numberingRuleService;

    @Override
    public String next(String ruleCode) { return numberingRuleService.next(ruleCode); }

    @Override
    public String next(String ruleCode, Map<String, Object> context) {
        return numberingRuleService.next(ruleCode, context);
    }
}
