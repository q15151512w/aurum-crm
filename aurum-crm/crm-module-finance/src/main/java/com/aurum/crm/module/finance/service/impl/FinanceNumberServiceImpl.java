package com.aurum.crm.module.finance.service.impl;

import com.aurum.crm.module.finance.service.FinanceNumberService;
import com.aurum.crm.module.system.service.NumberingRuleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

/** 财务编号适配器，保证回款和发票申请使用系统设置中的实时规则。 */
@Service
public class FinanceNumberServiceImpl implements FinanceNumberService {
    @Resource private NumberingRuleService numberingRuleService;

    @Override
    public String nextPaymentNumber() { return numberingRuleService.next("PAYMENT"); }

    @Override
    public String nextPaymentNumber(Map<String, Object> context) { return numberingRuleService.next("PAYMENT", context); }

    @Override
    public String nextInvoiceNumber() { return numberingRuleService.next("INVOICE"); }

    @Override
    public String nextInvoiceNumber(Map<String, Object> context) { return numberingRuleService.next("INVOICE", context); }
}
