package com.dgp.config;

import com.dgp.security.GatewayTrustInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SchedulerWebConfig implements WebMvcConfigurer {

    private final GatewayTrustInterceptor gatewayTrustInterceptor;

    public SchedulerWebConfig(GatewayTrustInterceptor gatewayTrustInterceptor) {
        this.gatewayTrustInterceptor = gatewayTrustInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(gatewayTrustInterceptor).addPathPatterns("/api/v1/**");
    }
}
