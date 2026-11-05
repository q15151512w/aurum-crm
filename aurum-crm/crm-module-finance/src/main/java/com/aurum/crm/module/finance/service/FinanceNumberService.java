package com.aurum.crm.module.finance.service;

import java.util.Map;

public interface FinanceNumberService {
    String nextPaymentNumber();
    String nextInvoiceNumber();
    String nextPaymentNumber(Map<String, Object> context);
    String nextInvoiceNumber(Map<String, Object> context);
}
