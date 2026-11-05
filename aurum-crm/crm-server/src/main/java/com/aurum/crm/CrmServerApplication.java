package com.aurum.crm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CRM 系统启动入口。
 *
 * <p>各业务模块通过 {@code @ComponentScan} 被自动扫描 ——
 * 只要包名在 {@code com.aurum.crm} 下，无需额外配置。
 */
@Slf4j
@EnableAsync        // 异步任务（操作日志落库、消息通知）
@EnableScheduling   // 定时任务（公海自动回收、合同到期扫描）
@SpringBootApplication
public class CrmServerApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(CrmServerApplication.class, args);
        Environment env = context.getEnvironment();

        String port = env.getProperty("server.port", "8080");
        String path = env.getProperty("server.servlet.context-path", "");
        String profile = String.join(",", env.getActiveProfiles());

        log.info("""

                ────────────────────────────────────────────────
                  AurumCRM 启动成功！
                ────────────────────────────────────────────────
                  当前环境 : {}
                  本地地址 : http://localhost:{}{}
                  接口文档 : http://localhost:{}{}/doc.html
                ────────────────────────────────────────────────
                """, profile, port, path, port, path);
    }
}
