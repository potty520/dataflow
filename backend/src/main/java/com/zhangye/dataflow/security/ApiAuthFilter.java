package com.zhangye.dataflow.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangye.dataflow.entity.SvcApplication;
import com.zhangye.dataflow.mapper.SvcApplicationMapper;
import com.zhangye.dataflow.service.RateLimitService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class ApiAuthFilter extends OncePerRequestFilter {

    private final SvcApplicationMapper appMapper;
    private final RateLimitService rateLimitService;

    public ApiAuthFilter(SvcApplicationMapper appMapper, RateLimitService rateLimitService) {
        this.appMapper = appMapper;
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith("/api/service/call/") && !path.startsWith("/api/service/api/") || !path.contains("/call")) {
            chain.doFilter(request, response);
            return;
        }

        String appKey = request.getHeader("X-App-Key");
        String appSecret = request.getHeader("X-App-Secret");

        if (appKey == null || appSecret == null || appKey.isEmpty() || appSecret.isEmpty()) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Missing app credentials\"}");
            return;
        }

        SvcApplication app = appMapper.selectOne(new LambdaQueryWrapper<SvcApplication>()
                .eq(SvcApplication::getAppKey, appKey)
                .eq(SvcApplication::getAppSecret, appSecret));

        if (app == null) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"Invalid app credentials\"}");
            return;
        }

        if (app.getStatus() != null && app.getStatus() == 0) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"Application is disabled\"}");
            return;
        }

        // Extract apiId from path: /api/service/api/{id}/call
        Long apiId = null;
        try {
            String[] parts = path.split("/");
            for (int i = 0; i < parts.length; i++) {
                if ("api".equals(parts[i]) && i + 1 < parts.length) {
                    apiId = Long.parseLong(parts[i + 1]);
                    break;
                }
            }
        } catch (NumberFormatException ignored) {}

        if (apiId != null) {
            if (!rateLimitService.allowRequest(apiId, app.getId())) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":429,\"message\":\"Rate limit exceeded\"}");
                return;
            }
        }

        request.setAttribute("apiAppId", app.getId());
        request.setAttribute("apiAppName", app.getAppName());
        chain.doFilter(request, response);
    }
}
