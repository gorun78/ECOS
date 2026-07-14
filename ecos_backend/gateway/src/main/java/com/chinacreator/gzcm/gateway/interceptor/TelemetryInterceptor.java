package com.chinacreator.gzcm.gateway.interceptor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;
import java.util.List;

/**
 * P3-5 Telemetry HTTP 拦截器 (OpenTelemetry 版)。
 * <p>
 * 使用 OpenTelemetry SDK 为每个 HTTP 请求创建 Span:
 * <ul>
 *   <li>W3C TraceContext 传播: 从 traceparent header 提取/生成 Trace ID</li>
 *   <li>Span 属性: http.method, http.path, http.status_code</li>
 *   <li>Span 通过 PostgresSpanExporter 自动写入 ecos_spans 表</li>
 *   <li>SpanContext 暂存 ThreadLocal，供业务代码注入属性</li>
 * </ul>
 */
@Component
public class TelemetryInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TelemetryInterceptor.class);

    private static final String SPAN_KEY = "telemetry.otelSpan";
    private static final String SCOPE_KEY = "telemetry.otelScope";

    /**
     * ThreadLocal 持有当前请求的 SpanContext，供业务代码获取 traceId/spanId。
     */
    private static final ThreadLocal<Span> CURRENT_SPAN = new ThreadLocal<>();

    private final Tracer tracer;
    private final TextMapPropagator propagator;

    public TelemetryInterceptor() {
        OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
        this.tracer = openTelemetry.getTracer("ecos-gateway", "1.0.0");
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
    }

    /**
     * 获取当前请求的 Span（供业务代码使用）。
     */
    public static Span getCurrentSpan() {
        return CURRENT_SPAN.get();
    }

    /**
     * 获取当前请求的 traceId（供业务代码使用）。
     */
    public static String getCurrentTraceId() {
        Span span = CURRENT_SPAN.get();
        return span != null ? span.getSpanContext().getTraceId() : null;
    }

    /**
     * B7: @PreDestroy — 应用关闭时优雅关闭 OpenTelemetry SDK，
     * 确保积压的 Span 被刷新到 PostgreSQL。
     */
    @PreDestroy
    public void shutdown() {
        log.info("TelemetryInterceptor shutting down OpenTelemetry SDK...");
        try {
            OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
            if (openTelemetry instanceof OpenTelemetrySdk) {
                OpenTelemetrySdk sdk = (OpenTelemetrySdk) openTelemetry;
                sdk.close();
                log.info("OpenTelemetry SDK closed successfully");
            }
        } catch (Exception e) {
            log.warn("Error during OpenTelemetry SDK shutdown: {}", e.getMessage());
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        // 1. 从 W3C traceparent header 提取 TraceContext
        Context extractedContext = propagator.extract(
                Context.current(),
                request,
                new ServletTextMapGetter());

        // 2. 创建 OTel Span
        String spanName = request.getMethod() + " " + request.getRequestURI();
        Span span = tracer.spanBuilder(spanName)
                .setParent(extractedContext)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("http.method", request.getMethod())
                .setAttribute("http.path", request.getRequestURI())
                .setAttribute("http.request_id", request.getHeader("X-Request-Id"))
                .startSpan();

        // 3. 激活 Span 作为当前作用域
        Scope scope = span.makeCurrent();

        request.setAttribute(SPAN_KEY, span);
        request.setAttribute(SCOPE_KEY, scope);
        CURRENT_SPAN.set(span);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        Span span = (Span) request.getAttribute(SPAN_KEY);
        Scope scope = (Scope) request.getAttribute(SCOPE_KEY);

        if (span == null) return;

        try {
            // 1. 记录 HTTP 状态码
            int status = response.getStatus();
            span.setAttribute("http.status_code", (long) status);
            span.setAttribute("http.response_content_length",
                    response.getHeader("Content-Length") != null
                            ? Long.parseLong(response.getHeader("Content-Length"))
                            : -1);

            // 2. 异常处理
            if (ex != null) {
                span.setStatus(StatusCode.ERROR, ex.getMessage());
                span.recordException(ex);
            } else if (status >= 400) {
                span.setStatus(StatusCode.ERROR, "HTTP " + status);
            } else {
                span.setStatus(StatusCode.OK);
            }

            // 3. 注入 traceparent 到响应 header（可选，便于上游链追踪）
            String traceparent = String.format("00-%s-%s-01",
                    span.getSpanContext().getTraceId(),
                    span.getSpanContext().getSpanId());
            response.setHeader("traceparent", traceparent);

            if (log.isDebugEnabled()) {
                log.debug("OTel Span: traceId={}, {} {} → {}",
                        span.getSpanContext().getTraceId(),
                        request.getMethod(), request.getRequestURI(), status);
            }

        } catch (Exception e) {
            log.warn("Telemetry afterCompletion error: {}", e.getMessage());
        } finally {
            // 4. 结束 Span — 触发 PostgresSpanExporter 写入
            span.end();

            // 5. 关闭作用域
            if (scope != null) {
                scope.close();
            }

            // 6. 清理 ThreadLocal
            CURRENT_SPAN.remove();
        }
    }

    /**
     * W3C TraceContext 提取器 — 从 HTTP 请求头提取 traceparent/tracestate。
     */
    private static class ServletTextMapGetter implements TextMapGetter<HttpServletRequest> {
        @Override
        public Iterable<String> keys(HttpServletRequest carrier) {
            List<String> headerNames = Collections.list(carrier.getHeaderNames());
            return headerNames;
        }

        @Override
        public String get(HttpServletRequest carrier, String key) {
            if (carrier == null) return null;
            return carrier.getHeader(key);
        }
    }
}
