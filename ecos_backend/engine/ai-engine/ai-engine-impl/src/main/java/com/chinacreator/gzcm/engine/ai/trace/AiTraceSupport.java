package com.chinacreator.gzcm.engine.ai.trace;

import com.chinacreator.gzcm.common.trace.EcosTraceContext;

/**
 * AI 域 traceId 读写门面 —— 全仓唯一实现在 {@link EcosTraceContext}（runtime/common-api）。
 *
 * <p>本类只做 AI 域内的可读别名，不另存第二套 trace 源：traceId 权威载体是 MDC
 * {@code traceId}，由入口过滤器（gateway {@code RequestContextFilter}，分册 00 承流）写入。</p>
 *
 * <p>纪律（详细设计-06 F06-06 要点 1）：{@code AgentTracer} 的内存 map 仅为本地调试视图，
 * 不作为 traceId 源；运行回放（F06-08）一律从结构化日志行 + 审计事件重建。</p>
 */
public final class AiTraceSupport {

    /** MDC 键名（转引底座常量，禁本域自定义第二字面量）。 */
    public static final String MDC_KEY_TRACE_ID = EcosTraceContext.MDC_KEY_TRACE_ID;

    /** 跨进程透传头名（转引底座常量）。 */
    public static final String HEADER_REQUEST_ID = EcosTraceContext.HEADER_REQUEST_ID;

    private AiTraceSupport() {
        throw new UnsupportedOperationException("工具类，禁止实例化");
    }

    /** 读 MDC traceId；缺失则生成完整 UUID 并写回 MDC。 */
    public static String currentTraceId() {
        return EcosTraceContext.currentTraceId();
    }

    /** 显式起始新链路（写入 MDC 并返回）。 */
    public static String newTraceId() {
        return EcosTraceContext.newTraceId();
    }

    /** 是否已有入口侧写入的 traceId（用于断言"未自建第二套 trace 源"）。 */
    public static boolean hasEntryTraceId() {
        return EcosTraceContext.hasEntryTraceId();
    }
}
