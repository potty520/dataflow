package com.dgp.common;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 生产 profile 禁止使用仓库内演示默认密钥，缺失或仍为默认值时拒绝启动。
 */
@Component
public class ProductionSecretGuard {

    private static final Logger log = LoggerFactory.getLogger(ProductionSecretGuard.class);

    private static final List<String> FORBIDDEN = List.of(
            "dev-gateway-hmac-change-me",
            "dgp-mask-key-2026",
            "dev-only-change-me-dgp-governance-platform-secret-key-2026"
    );

    private final Environment env;

    @Value("${dgp.gateway-hmac-secret:}")
    private String gatewayHmac;

    @Value("${dgp.mask-key:}")
    private String maskKey;

    @Value("${dgp.enforce-prod-secrets:true}")
    private boolean enforce;

    public ProductionSecretGuard(Environment env) {
        this.env = env;
    }

    @PostConstruct
    public void check() {
        boolean prod = Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "mysql".equalsIgnoreCase(p));
        if (!prod || !enforce) {
            return;
        }
        rejectIfBad("dgp.gateway-hmac-secret", gatewayHmac);
        rejectIfBad("dgp.mask-key", maskKey);
        String jwtPriv = env.getProperty("dgp.jwt-private-key", "");
        if (jwtPriv == null || jwtPriv.isBlank()) {
            // auth 服务必填；core 可无私钥
            if ("dgp-auth".equals(env.getProperty("spring.application.name"))) {
                throw new IllegalStateException("生产环境必须配置 DGP_JWT_PRIVATE_KEY / dgp.jwt-private-key");
            }
        }
        log.info("生产密钥检查通过（application={}）", env.getProperty("spring.application.name"));
    }

    private void rejectIfBad(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("生产环境缺少配置：" + name);
        }
        for (String bad : FORBIDDEN) {
            if (bad.equals(value)) {
                throw new IllegalStateException("生产环境禁止使用演示默认密钥：" + name);
            }
        }
    }
}
