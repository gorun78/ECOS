package com.chinacreator.gzcm.workspace.trace;

import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 卷07 F07-21（W185）—— 入站 X-Request-Id 继承（REQ-API-04）。
 *
 * <p>:18090 独立宿主进程不携带 gateway {@code RequestContextFilter}，本过滤器把入站头
 * 写入 MDC（{@link EcosTraceContext} 单源，禁 workspace 自建 traceId 生成器）；
 * gateway 宿主态 MDC 已由入口过滤器绑定 → 本类幂等不覆盖。链路结束只清理
 * 本过滤器绑定的键，防线程池复用串染。</p>
 */
@Component
public class WorkspaceTraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean boundHere = false;
        if (!EcosTraceContext.hasEntryTraceId()) {
            String inbound = request.getHeader(EcosTraceContext.HEADER_REQUEST_ID);
            if (inbound != null && !inbound.isBlank()) {
                MDC.put(EcosTraceContext.MDC_KEY_TRACE_ID, inbound.trim());
            } else {
                EcosTraceContext.currentTraceId();
            }
            boundHere = true;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (boundHere) {
                MDC.remove(EcosTraceContext.MDC_KEY_TRACE_ID);
            }
        }
    }
}
