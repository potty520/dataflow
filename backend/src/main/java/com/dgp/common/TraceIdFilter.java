package com.dgp.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/** 链路追踪：网关透传 X-Trace-Id，缺省生成 16 位；外部传入值必须符合白名单格式，避免日志注入。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    public static String current() {
        String v = HOLDER.get();
        return v == null ? "-" : v;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String supplied = req.getHeader("X-Trace-Id");
        String traceId = supplied != null && VALID.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        HOLDER.set(traceId);
        MDC.put("traceId", traceId);
        resp.setHeader("X-Trace-Id", traceId);
        try {
            chain.doFilter(req, resp);
        } finally {
            HOLDER.remove();
            MDC.remove("traceId");
        }
    }
}
