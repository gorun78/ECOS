package com.chinacreator.gzcm.common.trace;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * traceId 跨引擎唯一源（详细设计-06 F06-06 要点 1/2；铁律 §2.4）。
 *
 * <p>traceId 的权威载体是日志框架的 {@code MDC} 键 {@code traceId}，由入口过滤器
 * （gateway {@code RequestContextFilter}）从请求头 {@code X-Request-Id} 写入。
 * 本类是全仓唯一的读写门面：</p>
 * <ul>
 *   <li>{@link #currentTraceId()} —— 读 MDC；缺失时生成<b>完整 UUID</b> 并写回 MDC，
 *       使同一请求后续所有日志、审计事件、错误码诊断码同源（禁 8 位进程内短串，X-36）；</li>
 *   <li>{@link #newTraceId()} —— 无入口上下文的场景（调度器补跑、批处理）显式起一条新链；</li>
 *   <li>{@link #requestIdHeader()} —— 出站 HTTP 头名常量，供各底座客户端统一注入，
 *       禁调用点各自手写头名或自造 ID。</li>
 * </ul>
 *
 * <p>依赖纪律：只依赖 slf4j，不引入 Spring/Web，供 runtime 底座与各引擎同源使用。</p>
 */
public final class EcosTraceContext {

    /** MDC 键名 —— 与 logback pattern {@code %X{traceId}} 及 gateway 入口过滤器同字面量。 */
    public static final String MDC_KEY_TRACE_ID = "traceId";

    /** 跨引擎/跨进程透传头名（HTTP 头大小写不敏感，常量给唯一拼写）。 */
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    private EcosTraceContext() {
        throw new UnsupportedOperationException("工具类，禁止实例化");
    }

    /** 读 MDC traceId；缺失则生成完整 UUID 并写回 MDC 后返回。 */
    public static String currentTraceId() {
        String tid = MDC.get(MDC_KEY_TRACE_ID);
        if (tid != null && !tid.isBlank()) {
            return tid;
        }
        String generated = UUID.randomUUID().toString();
        MDC.put(MDC_KEY_TRACE_ID, generated);
        return generated;
    }

    /** 显式起始新链路（覆盖 MDC 并返回新 traceId）。 */
    public static String newTraceId() {
        String generated = UUID.randomUUID().toString();
        MDC.put(MDC_KEY_TRACE_ID, generated);
        return generated;
    }

    /** MDC 中是否已存在入口侧写入的 traceId（用于断言"未自建第二套 trace 源"）。 */
    public static boolean hasEntryTraceId() {
        String tid = MDC.get(MDC_KEY_TRACE_ID);
        return tid != null && !tid.isBlank();
    }

    /** 出站透传头名（唯一拼写入口，禁各调用点手写字符串）。 */
    public static String requestIdHeader() {
        return HEADER_REQUEST_ID;
    }

    /** 以 traceId 为值构造出站头（name/value 二元组，调用方自行塞入 headers 容器）。 */
    public static TraceHeader outboundHeader() {
        return new TraceHeader(HEADER_REQUEST_ID, currentTraceId());
    }

    /** 出站追踪头二元组。 */
    public record TraceHeader(String name, String value) {
    }
}
