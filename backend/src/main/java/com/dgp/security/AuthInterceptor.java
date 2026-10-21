package com.dgp.security;

import com.dgp.common.BizException;
import com.dgp.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 网关鉴权拦截器（本地形态）：JWT 校验 → 权限编码校验 → 审计落库。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 无需登录即可访问的白名单（精确匹配）。 */
    private static final Set<String> PUBLIC = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/jwks",
            "/api/v1/admin/ping"
    );

    private final AuthService authService;
    private final AuditService auditService;

    public AuthInterceptor(AuthService authService, AuditService auditService) {
        this.authService = authService;
        this.auditService = auditService;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            return true;
        }
        String uri = req.getRequestURI();
        if (PUBLIC.contains(uri)) {
            return true;
        }
        try {
            String auth = req.getHeader("Authorization");
            String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
            AuthUser user = authService.loadByToken(token);
            if (user == null) {
                throw new BizException(ErrorCode.UNAUTHORIZED);
            }
            AuthContext.set(user);

            if (handler instanceof HandlerMethod hm) {
                RequirePerm perm = hm.getMethodAnnotation(RequirePerm.class);
                if (perm == null) {
                    perm = hm.getBeanType().getAnnotation(RequirePerm.class);
                }
                if (perm != null && !user.has(perm.value())) {
                    throw new BizException(ErrorCode.FORBIDDEN, "缺少权限：" + perm.value());
                }
            }
            return true;
        } catch (RuntimeException e) {
            // preHandle 抛出异常时 afterCompletion 不会被调用，必须在此清理 ThreadLocal，
            // 否则登录上下文会残留在复用的 Tomcat 线程上（原实现的越权隐患）。
            AuthContext.clear();
            throw e;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        try {
            // 任何写操作后清空聚合读缓存：保证"改完立刻可见"，看板/统计类接口允许秒级缓存
            if (!"GET".equalsIgnoreCase(req.getMethod())) {
                com.dgp.common.TtlCache.clear();
            }
            if (!"GET".equalsIgnoreCase(req.getMethod()) && req.getRequestURI().startsWith("/api/v1/")) {
                String module = req.getRequestURI().split("/").length > 3 ? req.getRequestURI().split("/")[3] : "system";
                auditService.record(AuthContext.username(), req.getMethod(), module, req.getRequestURI(),
                        req.getQueryString(), clientIp(req), resp.getStatus() < 400 ? "SUCCESS" : "FAIL");
            }
        } finally {
            AuthContext.clear();
        }
    }

    public static String clientIp(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
