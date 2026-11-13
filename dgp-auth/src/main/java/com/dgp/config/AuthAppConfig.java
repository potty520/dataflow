package com.dgp.config;

import com.dgp.security.AuditService;
import com.dgp.security.GatewayTrustInterceptor;
import com.dgp.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class AuthAppConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthAppConfig.class);

    @Bean
    public JwtService jwtService(
            @Value("${dgp.jwt-private-key:}") String privatePem,
            @Value("${dgp.jwt-public-key:}") String publicPem,
            @Value("${dgp.jwt-key-id:dgp-1}") String keyId,
            @Value("${dgp.token-minutes:30}") long minutes,
            @Value("${dgp.refresh-days:7}") long refreshDays) {
        if (privatePem == null || privatePem.isBlank()) {
            log.warn("未配置 DGP_JWT_PRIVATE_KEY，已生成临时 RSA 密钥（仅限 local；多实例/网关需共享密钥）");
        }
        return JwtService.fromPemOrGenerate(privatePem, publicPem, keyId, minutes, refreshDays);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(RedisConnectionFactory.class)
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    public GatewayTrustInterceptor.AuditRecorder auditRecorder(AuditService auditService) {
        return auditService::record;
    }
}
