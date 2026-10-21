package com.dgp.config;

import com.dgp.security.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final String[] allowedOrigins;

    public WebConfig(AuthInterceptor authInterceptor,
                     @Value("${dgp.cors.allowed-origins:http://127.0.0.1:8080,http://localhost:8080,http://127.0.0.1:5173,http://localhost:5173}")
                     String origins) {
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = origins.split("\\s*,\\s*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**");
    }

    /** CORS 白名单：不再反射任意 Origin（原 allowedOriginPatterns("*") 等于对全网开放）。 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("X-Trace-Id")
                .maxAge(3600);
    }

    /** 前端构建产物（frontend/dist）与 classpath 静态资源双位置，支持一体化部署。 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("file:../frontend/dist/", "file:frontend/dist/", "classpath:/static/");
    }
}
