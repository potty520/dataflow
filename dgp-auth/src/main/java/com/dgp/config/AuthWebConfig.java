package com.dgp.config;

import com.dgp.common.BizException;
import com.dgp.common.ErrorCode;
import com.dgp.common.GatewayHeaders;
import com.dgp.security.AuthContext;
import com.dgp.security.AuthService;
import com.dgp.security.AuthUser;
import com.dgp.security.GatewayTrustInterceptor;
import com.dgp.security.JwtService;
import com.dgp.security.RequirePerm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Set;

@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

    private final GatewayTrustInterceptor gatewayTrustInterceptor;
    private final AuthService authService;
    private final JwtService jwtService;

    public AuthWebConfig(GatewayTrustInterceptor gatewayTrustInterceptor,
                         AuthService authService, JwtService jwtService) {
        this.gatewayTrustInterceptor = gatewayTrustInterceptor;
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            private final Set<String> PUBLIC = Set.of(
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/jwks",
                    "/api/v1/auth/.well-known/jwks.json"
            );

            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
                if ("OPTIONS".equalsIgnoreCase(req.getMethod())) return true;
                String uri = req.getRequestURI();
                if (uri.startsWith("/actuator")) return true;
                if (PUBLIC.contains(uri) || uri.startsWith("/api/v1/auth/cas/")) return true;
                // 优先信任网关头；本地直连时回退 JWT（便于调试）
                String sign = req.getHeader(GatewayHeaders.GATEWAY_SIGN);
                if (sign != null && !sign.isBlank()) {
                    return gatewayTrustInterceptor.preHandle(req, resp, handler);
                }
                String auth = req.getHeader("Authorization");
                String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
                AuthUser user = authService.loadByToken(token);
                if (user == null) throw new BizException(ErrorCode.UNAUTHORIZED);
                AuthContext.set(user);
                if (handler instanceof HandlerMethod hm) {
                    RequirePerm perm = hm.getMethodAnnotation(RequirePerm.class);
                    if (perm == null) perm = hm.getBeanType().getAnnotation(RequirePerm.class);
                    if (perm != null && !user.has(perm.value())) {
                        AuthContext.clear();
                        throw new BizException(ErrorCode.FORBIDDEN, "缺少权限：" + perm.value());
                    }
                }
                return true;
            }

            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                        Object handler, Exception ex) {
                AuthContext.clear();
            }
        }).addPathPatterns("/api/v1/**");
    }
}
