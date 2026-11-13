package com.dgp.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * 为 API 响应补 Content-Length，避免使用 Transfer-Encoding: chunked 分块传输。
 *
 * <p>背景：公网链路上部分代理/网关对分块响应处理不规范，浏览器端会报
 * ERR_INVALID_CHUNKED_ENCODING 并丢弃该响应（服务端本机 curl 连打 240 次 0 截断，
 * 说明是链路而非服务端问题）。改成定长响应后可彻底规避该类客户端错误。
 *
 * <p>仅处理 /api/** 的非流式响应：SSE（/stream）保持流式语义，超大响应（>4MB）直接透传。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class ContentLengthFilter extends OncePerRequestFilter {

    private static final int MAX_BUFFER = 4 * 1024 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/") || uri.contains("/stream");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(resp);
        chain.doFilter(req, wrapper);

        String contentType = wrapper.getContentType();
        if (contentType != null && contentType.startsWith("text/event-stream")) {
            wrapper.copyBodyToResponse();
            return;
        }
        byte[] body = wrapper.getContentAsByteArray();
        if (body.length > MAX_BUFFER) {
            wrapper.copyBodyToResponse();
            return;
        }
        wrapper.resetBuffer();
        resp.setContentLength(body.length);
        if (body.length > 0) {
            resp.getOutputStream().write(body);
        }
    }
}
