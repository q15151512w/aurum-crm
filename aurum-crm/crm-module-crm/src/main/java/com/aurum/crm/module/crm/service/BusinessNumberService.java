package com.aurum.crm.module.crm.service;

import java.util.Map;

public interface BusinessNumberService {
    String next(String ruleCode);
    String next(String ruleCode, Map<String, Object> context);
}
