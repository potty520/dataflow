package com.dgp.config;

import com.dgp.security.AuditService;
import com.dgp.security.GatewayTrustInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final GatewayTrustInterceptor gatewayTrustInterceptor;

    public WebConfig(GatewayTrustInterceptor gatewayTrustInterceptor) {
        this.gatewayTrustInterceptor = gatewayTrustInterceptor;
    }

    @Bean
    public GatewayTrustInterceptor.AuditRecorder auditRecorder(AuditService auditService) {
        return auditService::record;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(gatewayTrustInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/admin/ping");
    }

    /** CORS 由网关统一处理；core 不再对浏览器开放跨域。 */

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("file:../frontend/dist/", "file:frontend/dist/", "classpath:/static/");
    }
}
