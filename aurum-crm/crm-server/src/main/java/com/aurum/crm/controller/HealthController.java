package com.aurum.crm.controller;

import com.aurum.crm.common.core.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查。
 *
 * <p>用于验证骨架是否搭建成功 —— 启动后访问
 * {@code http://localhost:8080/health} 应返回 {@code code: 0}。
 *
 * <p>注意路径在 {@code /health} 而不是 {@code /actuator/health}，
 * 后者由 Spring Boot Actuator 提供（检查的是数据库、Redis 等组件是否连通）。
 */
@Tag(name = "健康检查")
@RestController
@RequestMapping("/health")
public class HealthController {

    @Operation(summary = "服务健康检查")
    @GetMapping
    public CommonResult<Map<String, Object>> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("application", "aurum-crm");
        info.put("status", "UP");
        info.put("time", LocalDateTime.now());
        return CommonResult.success(info);
    }
}
