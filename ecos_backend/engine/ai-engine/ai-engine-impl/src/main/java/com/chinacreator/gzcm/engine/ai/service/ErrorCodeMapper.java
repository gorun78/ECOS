package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.common.error.AiError;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLException;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * 智能域异常分类器 —— 详细设计-06 F06-14 要点 1：{@code classify(Throwable, context) → AiError}。
 *
 * <p>本类是智能域唯一的异常→错误类映射单源（原"异常类名 → 友好文案"形态会 {@code return msg}
 * 把异常原文带进响应面，属 X-49 泄露面，已废弃）。分类结果只携带
 * {@link AiErrorClass} + 渲染后的模板消息；原始原因仅作 {@code rawReason} 随对象入日志，
 * 禁进响应体 / SSE 帧。</p>
 *
 * <p>分类原则（"边界才捕、捕后必分类"）：</p>
 * <ul>
 *   <li>{@link Context#surface} 决定同一异常落在哪一类 —— provider 超时属
 *       {@code LLM_TIMEOUT}，工具下游超时属 {@code TOOL_BACKEND_ERROR}；</li>
 *   <li>沿 cause 链自外向内找<b>第一个可识别</b>异常，识别不到才按面兜底；</li>
 *   <li>兜底类不得为 404（禁 {@code Exception → 404} 掩蔽复发，分册 01 接缝）。</li>
 * </ul>
 */
public final class ErrorCodeMapper {

    /** 异常发生的调用面。 */
    public enum Surface {
        /** LLM 出口（llm-gateway 及其调用方） */
        LLM,
        /** 工具/数据服务下游 */
        TOOL,
        /** 本引擎持久化 */
        PERSISTENCE,
        /** 其它（控制器边界兜底） */
        GENERIC
    }

    /**
     * 分类上下文。
     *
     * @param surface 调用面，决定兜底类
     * @param service 下游服务名（{@code TOOL_BACKEND_ERROR} 的 {@code {service}} 占位）
     */
    public record Context(Surface surface, String service) {

        public static Context llm(String service) {
            return new Context(Surface.LLM, service);
        }

        public static Context tool(String service) {
            return new Context(Surface.TOOL, service);
        }

        public static Context persistence(String service) {
            return new Context(Surface.PERSISTENCE, service);
        }

        public static Context generic() {
            return new Context(Surface.GENERIC, "ai-engine");
        }
    }

    /** provider 限流状态码（429 走降级面，禁引擎侧绕行）。 */
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    /** 上游过载/网关类状态码：与超时同属可退避重试面。 */
    private static final int HTTP_BAD_GATEWAY = 502;
    private static final int HTTP_SERVICE_UNAVAILABLE = 503;
    private static final int HTTP_GATEWAY_TIMEOUT = 504;

    /** 上下文超限状态码（413）。 */
    private static final int HTTP_PAYLOAD_TOO_LARGE = 413;

    private ErrorCodeMapper() {
    }

    /**
     * 异常 → {@link AiError}：必落 {@code ECOS-AI-*} 某类，禁返回语义成功值。
     *
     * @param throwable 原始异常（可为 null，null 时按面兜底）
     * @param context   调用面与服务名
     * @param traceId   MDC traceId（诊断码唯一源）
     */
    public static AiError classify(Throwable throwable, Context context, String traceId) {
        Context ctx = context == null ? Context.generic() : context;
        AiErrorClass errorClass = resolve(throwable, ctx);
        String rawReason = throwable == null ? null : rootReason(throwable);
        return new AiError(errorClass, traceId, 0, rawReason, serviceLabel(ctx));
    }

    /**
     * llm-gateway 失败响应 → {@link AiError}：网关已按 F06-14 归类，引擎侧只渲染不再二次猜测。
     *
     * <p>网关未上报分类（第三方实现或旧链路）时落 {@code TOOL_BACKEND_ERROR}（下游服务异常，
     * {@code service=llm-gateway}）并 ERROR 留痕，禁按 {@code errorMsg} 字面猜类，更禁伪造成功。</p>
     */
    public static AiError classify(ChatResponse response, String traceId) {
        if (response == null) {
            return new AiError(AiErrorClass.LLM_TIMEOUT, traceId, 0, "LLM 调用返回 null", "llm-gateway");
        }
        AiErrorClass cls = aiErrorClassOf(response.getErrorClass());
        if (cls == null) {
            return new AiError(AiErrorClass.TOOL_BACKEND_ERROR, traceId, 0,
                    "网关未上报错误分类: " + response.getErrorMsg(), "llm-gateway");
        }
        return new AiError(cls, traceId, response.getRetriesAttempted(),
                response.getErrorMsg(), "llm-gateway");
    }

    /** 错误码字面量 → 枚举名（不可识别返回 null，禁伪造分类）。 */
    public static String errorCodeName(String errorCode) {
        for (AiErrorClass c : AiErrorClass.values()) {
            if (errorCode != null && errorCode.equals(c.errorCode())) {
                return c.name();
            }
        }
        return null;
    }

    /** 枚举名 → {@link AiErrorClass}（不可识别返回 null）。 */
    public static AiErrorClass aiErrorClassOf(String errorClassName) {
        if (errorClassName == null || errorClassName.isBlank()) {
            return null;
        }
        for (AiErrorClass c : AiErrorClass.values()) {
            if (c.name().equals(errorClassName)) {
                return c;
            }
        }
        return null;
    }

    // ── 内部分解 ────────────────────────────────────────────────────────

    private static AiErrorClass resolve(Throwable throwable, Context ctx) {
        Throwable t = throwable;
        while (t != null) {
            AiErrorClass cls = classifyOne(t, ctx);
            if (cls != null) {
                return cls;
            }
            t = t.getCause() == t ? null : t.getCause();
        }
        return switch (ctx.surface()) {
            case LLM -> AiErrorClass.LLM_TIMEOUT;
            case TOOL, PERSISTENCE, GENERIC -> AiErrorClass.TOOL_BACKEND_ERROR;
        };
    }

    /** 单个异常类型 → 错误类；不可识别返回 null 交给 cause 链继续走。 */
    private static AiErrorClass classifyOne(Throwable t, Context ctx) {
        if (t instanceof IllegalArgumentException) {
            return AiErrorClass.INVALID_ARGUMENT;
        }
        if (t instanceof JsonProcessingException
                || t instanceof IllegalStateException && isParseFailure(t)) {
            return AiErrorClass.PARSE_FAILURE;
        }
        if (t instanceof SSLException || t instanceof UnknownHostException || t instanceof ConnectException) {
            return ctx.surface() == Surface.LLM ? AiErrorClass.LLM_TIMEOUT : AiErrorClass.TOOL_BACKEND_ERROR;
        }
        if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException
                || t instanceof TimeoutException) {
            return ctx.surface() == Surface.LLM ? AiErrorClass.LLM_TIMEOUT : AiErrorClass.TOOL_BACKEND_ERROR;
        }
        if (t instanceof IOException) {
            return ctx.surface() == Surface.LLM ? AiErrorClass.LLM_TIMEOUT : AiErrorClass.TOOL_BACKEND_ERROR;
        }
        Integer status = httpStatusOf(t);
        if (status != null) {
            return fromHttpStatus(status, ctx);
        }
        if (isDataAccessFailure(t)) {
            return AiErrorClass.TOOL_BACKEND_ERROR;
        }
        return null;
    }

    /**
     * HTTP 状态码 → 错误类。
     *
     * <p>429 与 5xx 是唯二可退避重试面；其余按 D.5 码表落对应类。401/403 由护栏面单独上报
     * （本分类器不臆断权限语义，避免把 provider 鉴权失败误报成 GUARDRAIL_DENIED）。</p>
     */
    private static AiErrorClass fromHttpStatus(int status, Context ctx) {
        if (status == HTTP_TOO_MANY_REQUESTS) {
            return AiErrorClass.LLM_RATE_LIMITED;
        }
        if (status == HTTP_PAYLOAD_TOO_LARGE) {
            return AiErrorClass.CONTEXT_OVERFLOW;
        }
        if (status == HTTP_GATEWAY_TIMEOUT || status == HTTP_SERVICE_UNAVAILABLE
                || status == HTTP_BAD_GATEWAY) {
            return ctx.surface() == Surface.LLM ? AiErrorClass.LLM_TIMEOUT : AiErrorClass.TOOL_BACKEND_ERROR;
        }
        if (status >= 500) {
            return ctx.surface() == Surface.LLM ? AiErrorClass.LLM_TIMEOUT : AiErrorClass.TOOL_BACKEND_ERROR;
        }
        if (status == 404) {
            return AiErrorClass.NOT_FOUND;
        }
        if (status >= 400) {
            return AiErrorClass.INVALID_ARGUMENT;
        }
        return null;
    }

    /** 反射取下游 HTTP 状态码（避免本类依赖 spring-web 的 ResponseStatusException 具体类型）。 */
    private static Integer httpStatusOf(Throwable t) {
        try {
            Object status = t.getClass().getMethod("getStatus").invoke(t);
            if (status instanceof Number n) {
                return n.intValue();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // 该异常不带状态码，交给类型链继续判定
        }
        return null;
    }

    /** 表缺失/SQL 语法类失败 —— 归"数据服务异常"，禁掩蔽为 404。 */
    private static boolean isDataAccessFailure(Throwable t) {
        String name = t.getClass().getName();
        return name.startsWith("org.springframework.dao.")
                || t instanceof java.sql.SQLException;
    }

    /** LLM 输出结构解析失败的显式标记（Service 层抛 IllegalStateException 时以该前缀声明）。 */
    private static boolean isParseFailure(Throwable t) {
        String msg = t.getMessage();
        return msg != null && (msg.startsWith("LLM_OUTPUT_PARSE") || msg.startsWith("PARSE_FAILURE"));
    }

    private static String serviceLabel(Context ctx) {
        return ctx.service() == null || ctx.service().isBlank() ? "ai-engine" : ctx.service();
    }

    /** 最内层原因摘要（仅日志用；禁进响应体）。 */
    private static String rootReason(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getClass().getSimpleName() + ": " + c.getMessage();
    }
}
