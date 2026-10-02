package com.chinacreator.gzcm.workspace.trace;

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
 * F07-21 / C167 (X-55 病根) — 场景独立部署单元 ({@code :18090}) 的 traceId 接力过滤器。
 *
 * <p><b>为什么另立一件</b>：gateway 侧的 {@code RequestContextFilter}
 * 在 {@code com.chinacreator.gzcm.gateway.filter.*} 包，
 * {@code WorkspaceServiceApplication} 的 {@code @ComponentScan} 覆盖
 * {@code com.chinacreator.gzcm.workspace.*} 但不含 {@code .gateway.*}，
 * 独立态（R-24=②）下 workspace 进程 68 端点全无 MDC，
 * {@link com.chinacreator.gzcm.workspace.QueryHistoryService} 落 V212 表
 * {@code trace_id VARCHAR(64)} 全为 NULL，F07-21 "traceId 全链贯通" 断链。</p>
 *
 * <p><b>幂等契约（可挂 gateway fat-JAR 无冲突）</b>：
 * <ol>
 *   <li>MDC 已有 traceId（gateway 侧 filter 先跑透传）→ 只回带响应头，不再生成/再清</li>
 *   <li>MDC 无 traceId → {@code X-Request-Id} 8~36 位合格则透传，否则 UUID 生成</li>
 *   <li>结束时 <b>仅本 filter 是生成者才清 MDC</b>（防止 gateway 侧 filter 已在最外层时
 *       本 filter 先清导致最外层 filter 的 finally 拿到脏状态；独立态下即唯一生成者，清正合适）</li>
 * </ol>
 *
 * <p>链序 {@link Ordered#LOWEST_PRECEDENCE}：确保在 gateway {@code RequestContextFilter}
 * (HIGHEST+11) 之后运行；独立态下独自在链上，效果等同首跑。</p>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class WorkspaceTraceIdFilter extends OncePerRequestFilter {

    private static final int TRACE_MIN = 8;
    private static final int TRACE_MAX = 36;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String existing = TraceContext.current();
        boolean ownedByMe = false;
        String traceId;
        if (existing != null && !existing.isBlank()) {
            // gateway 侧 filter 已设 (monolith 场景)，本 filter 只透传响应头
            traceId = existing;
        } else {
            traceId = request.getHeader(TraceContext.HEADER_NAME);
            if (traceId == null || traceId.length() < TRACE_MIN || traceId.length() > TRACE_MAX) {
                traceId = UUID.randomUUID().toString();
            }
            TraceContext.put(traceId);
            ownedByMe = true;
        }
        response.setHeader(TraceContext.HEADER_NAME, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            if (ownedByMe) {
                TraceContext.clear();
            }
        }
    }
}
