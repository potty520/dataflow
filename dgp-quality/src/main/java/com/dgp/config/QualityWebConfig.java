package com.dgp.config;

import com.dgp.security.AuditService;
import com.dgp.security.GatewayTrustInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class QualityWebConfig implements WebMvcConfigurer {

    private final GatewayTrustInterceptor gatewayTrustInterceptor;

    public QualityWebConfig(GatewayTrustInterceptor gatewayTrustInterceptor) {
        this.gatewayTrustInterceptor = gatewayTrustInterceptor;
    }

    @Bean
    public GatewayTrustInterceptor.AuditRecorder auditRecorder(AuditService auditService) {
        return auditService::record;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(gatewayTrustInterceptor).addPathPatterns("/api/v1/**");
    }
}
