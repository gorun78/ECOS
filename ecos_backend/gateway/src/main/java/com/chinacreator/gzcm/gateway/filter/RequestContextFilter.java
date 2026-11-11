package com.chinacreator.gzcm.gateway.filter;

import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;
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

/**
 * C.5.1 traceId 入口过滤器（详细设计-00 · W08/W09 · M0）。
 *
 * <p>职责：
 * <ol>
 *   <li>接受/生成 {@code X-Request-Id}（客户端提供则沿用，否则 {@link UUID#randomUUID()}）</li>
 *   <li>写入 MDC 键 {@code traceId}，让 logback pattern {@code %X{traceId}} 与业务日志破案</li>
 *   <li>回写响应头 {@code X-Request-Id}，供前端/BFF 反向串联告警工单</li>
 *   <li>{@code finally} 里显式 {@link MDC#remove(String)}，防线程池复用串号</li>
 * </ol>
 *
 * <p>顺序：{@code HIGHEST_PRECEDENCE+11}，紧随 {@code VersionPrefixRewriteFilter(+10)}，
 * 早于 {@code EcosTrustHeaderFilter(+20)} 与 {@code SecurityHeadersFilter(+30)}——
 * 这样 trust 头剥离阶段 MDC 已经就绪，任何异常日志都能挂上 traceId。</p>
 */
@Component("ecosTraceContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
public class RequestContextFilter extends OncePerRequestFilter {

    public static final String MDC_KEY_TRACE = "traceId";
    public static final String HEADER_TRACE  = HeaderAuthInterceptor.H_TRACE;
    /** 新头名（详设-16 §16.4 / CLI 侧统一用 X-Trace-Id）；旧名继续兼容。 */
    public static final String HEADER_TRACE_CANONICAL = "X-Trace-Id";
    public static final String RESPONSE_HEADER_TRACE = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String inbound = firstNonBlank(request.getHeader(HEADER_TRACE_CANONICAL),
                request.getHeader(HEADER_TRACE));
        String traceId = (inbound == null || inbound.isBlank())
                ? UUID.randomUUID().toString().replace("-", "")
                : inbound.trim();

        MDC.put(MDC_KEY_TRACE, traceId);
        // 双写：新头名供 CLI/新调用方读取，旧头名保证既有调用方（读 X-Request-Id）不破
        response.setHeader(HEADER_TRACE_CANONICAL, traceId);
        response.setHeader(RESPONSE_HEADER_TRACE, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY_TRACE);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
