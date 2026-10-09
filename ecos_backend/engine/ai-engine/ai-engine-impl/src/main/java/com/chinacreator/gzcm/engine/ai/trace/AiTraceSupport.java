package com.chinacreator.gzcm.engine.ai.trace;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * traceId 唯一源读写（详细设计-06 F06-06 要点 1）。
 *
 * <p>traceId 的权威来源是 MDC {@code traceId}，由入口过滤器（gateway
 * {@code RequestContextFilter}，分册 00 承流）写入。本类只做两件事：</p>
 * <ol>
 *   <li>{@link #currentTraceId()}：读 MDC；缺失时生成完整 UUID 并<b>写回 MDC</b>，
 *       使同一请求后续所有日志/审计/错误码诊断码同源（禁 8 位进程内短串，X-36）；</li>
 *   <li>{@link #newTraceId()}：为新会话/新 Run 显式起一条链。</li>
 * </ol>
 *
 * <p>{@code AgentTracer} 的内存 map 只是本地调试视图，不作为 traceId 源。</p>
 */
public final class AiTraceSupport {

    public static final String MDC_KEY_TRACE_ID = "traceId";

    private AiTraceSupport() {
        throw new UnsupportedOperationException("工具类，禁止实例化");
    }

    /** 读 MDC traceId；缺失则生成完整 UUID 并写回 MDC。 */
    public static String currentTraceId() {
        String tid = MDC.get(MDC_KEY_TRACE_ID);
        if (tid != null && !tid.isBlank()) {
            return tid;
        }
        String generated = UUID.randomUUID().toString();
        MDC.put(MDC_KEY_TRACE_ID, generated);
        return generated;
    }

    /** 显式起始新链路（写入 MDC 并返回）。 */
    public static String newTraceId() {
        String generated = UUID.randomUUID().toString();
        MDC.put(MDC_KEY_TRACE_ID, generated);
        return generated;
    }

    /** 是否已有入口侧写入的 traceId（用于断言"未自建第二套 trace 源"）。 */
    public static boolean hasEntryTraceId() {
        String tid = MDC.get(MDC_KEY_TRACE_ID);
        return tid != null && !tid.isBlank();
    }
}
