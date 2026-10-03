package com.chinacreator.gzcm.common.context;

import org.slf4j.MDC;

/**
 * 全链 traceId 上下文（详细设计-00 W08/F00-11）。
 * <p>
 * 单一 MDC key {@code traceId}，入口由 gateway {@code RequestContextFilter} 写入
 * （{@code X-Request-Id} 有则透传、无则生成），跨 JVM 由
 * {@code HeaderAuthInterceptor} 从同一请求头再入 MDC。日志 pattern 用 {@code [%X{traceId}]}，
 * {@code ApiResponse} 序列化时经 {@link #current()} 回填。
 */
public final class TraceContext {

    /** 全仓唯一 MDC key */
    public static final String MDC_KEY = "traceId";

    /** 端到端请求 ID 请求头 */
    public static final String HEADER_NAME = "X-Request-Id";

    private TraceContext() {
    }

    /** @return 当前线程 traceId，无则 null */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static void put(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(MDC_KEY, traceId.trim());
        }
    }

    /** 无条件清空（afterCompletion / finally 必须调用，防线程池串号） */
    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
