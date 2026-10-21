package com.dgp.config;

import com.dgp.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private static final String DEV_DEFAULT_SECRET =
            "dev-only-change-me-dgp-governance-platform-secret-key-2026";

    @Bean
    public JwtService jwtService(@Value("${dgp.jwt-secret}") String secret,
                                 @Value("${dgp.token-minutes:30}") long minutes,
                                 @Value("${dgp.refresh-days:7}") long refreshDays) {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("dgp.jwt-secret 至少需要 32 字节，请通过环境变量 DGP_JWT_SECRET 注入");
        }
        if (DEV_DEFAULT_SECRET.equals(secret)) {
            log.warn("当前使用开发默认 JWT 密钥，生产环境必须通过 DGP_JWT_SECRET 覆盖");
        }
        return new JwtService(secret, minutes, refreshDays);
    }

    /** 调度线程池：避免 ETL/稽核等长任务串行阻塞其它定时任务（原默认单线程池）。 */
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(4);
        s.setThreadNamePrefix("dgp-sched-");
        s.setWaitForTasksToCompleteOnShutdown(true);
        s.setAwaitTerminationSeconds(20);
        return s;
    }
}
