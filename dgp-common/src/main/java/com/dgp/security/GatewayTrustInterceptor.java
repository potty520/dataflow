package com.dgp.security;

import com.dgp.common.BizException;
import com.dgp.common.ErrorCode;
import com.dgp.common.GatewayHeaders;
import com.dgp.common.GatewayHmac;
import com.dgp.common.TtlCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 下游服务鉴权：信任网关签发的内部身份头（HMAC），并校验 {@link RequirePerm}。
 */
@Component
public class GatewayTrustInterceptor implements HandlerInterceptor {

    private final String gatewaySecret;
    private final long maxSkewSeconds;
    private final String schedulerToken;
    private AuditRecorder auditRecorder;

    public interface AuditRecorder {
        void record(String username, String action, String module, String resource,
                    String detail, String ip, String result);
    }

    public GatewayTrustInterceptor(
            @Value("${dgp.gateway-hmac-secret:dev-gateway-hmac-change-me}") String gatewaySecret,
            @Value("${dgp.gateway-hmac-skew-seconds:300}") long maxSkewSeconds,
            @Value("${dgp.scheduler-token:dev-scheduler-token-change-me}") String schedulerToken) {
        this.gatewaySecret = gatewaySecret;
        this.maxSkewSeconds = maxSkewSeconds;
        this.schedulerToken = schedulerToken;
    }

    @Autowired(required = false)
    public void setAuditRecorder(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            return true;
        }
        String uri = req.getRequestURI();
        if (uri.startsWith("/actuator") || "/api/v1/admin/ping".equals(uri)) {
            return true;
        }
        if (uri.startsWith("/api/v1/internal/scheduler") && schedulerTokenOk(req)) {
            AuthContext.set(new AuthUser(0L, "scheduler", "调度服务", "ROLE_GOV_ADMIN", 1L,
                    java.util.Set.of("*")));
            return true;
        }
        if ("1".equals(req.getHeader(GatewayHeaders.INTERNAL_SKIP_AUTH))) {
            return true;
        }
        try {
            AuthUser user = resolveUser(req);
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
            AuthContext.clear();
            throw e;
        }
    }

    private AuthUser resolveUser(HttpServletRequest req) {
        String ts = req.getHeader(GatewayHeaders.GATEWAY_TS);
        String sign = req.getHeader(GatewayHeaders.GATEWAY_SIGN);
        String userId = req.getHeader(GatewayHeaders.USER_ID);
        String username = req.getHeader(GatewayHeaders.USERNAME);
        String role = req.getHeader(GatewayHeaders.ROLE);
        String tenantId = req.getHeader(GatewayHeaders.TENANT_ID);
        if (username == null || username.isBlank()) {
            return null;
        }
        if (ts == null || sign == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少网关签名");
        }
        long skew;
        try {
            skew = Math.abs(System.currentTimeMillis() / 1000 - Long.parseLong(ts));
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "网关时间戳非法");
        }
        if (skew > maxSkewSeconds) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "网关签名过期");
        }
        String payload = GatewayHeaders.signPayload(userId, username, role, tenantId, ts);
        if (!GatewayHmac.verify(gatewaySecret, payload, sign)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "网关签名校验失败");
        }
        String permsHeader = req.getHeader(GatewayHeaders.PERMS);
        Set<String> perms = permsHeader == null || permsHeader.isBlank()
                ? Set.of()
                : Arrays.stream(permsHeader.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        String realName = req.getHeader(GatewayHeaders.REAL_NAME);
        long uid = 0L;
        long tid = 1L;
        try {
            if (userId != null && !userId.isBlank()) uid = Long.parseLong(userId);
            if (tenantId != null && !tenantId.isBlank()) tid = Long.parseLong(tenantId);
        } catch (NumberFormatException ignored) {
        }
        return new AuthUser(uid, username, realName == null ? username : realName,
                role == null ? "" : role, tid, perms);
    }

    private boolean schedulerTokenOk(HttpServletRequest req) {
        String token = req.getHeader(GatewayHeaders.SCHEDULER_TOKEN);
        return schedulerToken != null && !schedulerToken.isBlank()
                && schedulerToken.equals(token);
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        try {
            if (!"GET".equalsIgnoreCase(req.getMethod())) {
                TtlCache.clear();
            }
            if (!"GET".equalsIgnoreCase(req.getMethod()) && req.getRequestURI().startsWith("/api/v1/")
                    && auditRecorder != null) {
                String module = req.getRequestURI().split("/").length > 3
                        ? req.getRequestURI().split("/")[3] : "system";
                auditRecorder.record(AuthContext.username(), req.getMethod(), module, req.getRequestURI(),
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
