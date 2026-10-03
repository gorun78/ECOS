package com.chinacreator.gzcm.gateway.filter;

import com.chinacreator.gzcm.common.context.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * W08 / F00-11 — 全链 traceId 入口过滤器（详细设计-00 C.1.1/C.5.1）。
 * <p>
 * 链序 {@code HIGHEST_PRECEDENCE+11}（重写+10 之后、信任剥离+20 之前）：
 * <ol>
 *   <li>请求带 {@code X-Request-Id}（8~36 位）→ 透传；否则生成 UUID</li>
 *   <li>写入 MDC（key {@code traceId}，经 {@link TraceContext}）</li>
 *   <li>响应回带 {@code X-Request-Id}，前端可展示/复制</li>
 *   <li>请求结束清除 MDC（防线程池串号）</li>
 * </ol>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
public class RequestContextFilter extends OncePerRequestFilter {

    private static final int TRACE_MIN = 8;
    private static final int TRACE_MAX = 36;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = request.getHeader(TraceContext.HEADER_NAME);
        if (traceId == null || traceId.length() < TRACE_MIN || traceId.length() > TRACE_MAX) {
            traceId = UUID.randomUUID().toString();
        }
        response.setHeader(TraceContext.HEADER_NAME, traceId);
        TraceContext.put(traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }

    /** B 章状态矩阵用：当前请求 traceId（诊断页页脚展示） */
    public static String currentTraceId() {
        return TraceContext.current();
    }
}
