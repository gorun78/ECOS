package com.chinacreator.gzcm.common.base;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.chinacreator.gzcm.common.context.TraceContext;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * 统一 API 响应封装。
 * <p>
 * 所有 Controller 返回此类型。前端/外部调用方只需判断 {@code code} 字段：
 * <ul>
 *   <li>{@code 0} — 成功</li>
 *   <li>{@code > 0} — 业务错误（参数校验失败、资源不存在等）</li>
 *   <li>{@code < 0} — 系统错误（数据库异常、外部服务不可用等）</li>
 * </ul>
 *
 * @param <T> 业务数据载荷类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // ── 状态码 ──────────────────────────────────────
    /** 成功 */
    public static final int CODE_SUCCESS = 0;
    /** 通用业务错误 */
    public static final int CODE_BAD_REQUEST = 400;
    /** 未认证 */
    public static final int CODE_UNAUTHORIZED = 401;
    /** 无权限 */
    public static final int CODE_FORBIDDEN = 403;
    /** 资源不存在 */
    public static final int CODE_NOT_FOUND = 404;
    /** 系统内部错误 */
    public static final int CODE_INTERNAL_ERROR = -1;

    // ── 字段 ────────────────────────────────────────
    private int code;
    private String errorCode;
    private String message;
    private T data;
    private Long timestamp;
    /** W09（详细设计-00 D.5.1）：全链 traceId，增量字段，序列化向后兼容（NON_NULL） */
    private String traceId;

    // ── 构造器 ──────────────────────────────────────

    private ApiResponse() {
        this.timestamp = Instant.now().toEpochMilli();
    }

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now().toEpochMilli();
    }

    private ApiResponse(int code, String errorCode, String message, T data) {
        this.code = code;
        this.errorCode = errorCode;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now().toEpochMilli();
    }

    // ── 工厂方法 ────────────────────────────────────

    /** 成功（无数据） */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(CODE_SUCCESS, "ok", null);
    }

    /** 成功（带数据） */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(CODE_SUCCESS, "ok", data);
    }

    /** 成功（自定义消息 + 数据） */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(CODE_SUCCESS, message, data);
    }

    /** 失败 */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /** 失败（带errorCode，用于精细客户端处理） */
    public static <T> ApiResponse<T> error(int code, String errorCode, String message) {
        return new ApiResponse<>(code, errorCode, message, null);
    }

    /** 业务错误 */
    public static <T> ApiResponse<T> badRequest(String message) {
        return new ApiResponse<>(CODE_BAD_REQUEST, message, null);
    }

    /** 未授权 */
    public static <T> ApiResponse<T> unauthorized(String message) {
        return new ApiResponse<>(CODE_UNAUTHORIZED, message, null);
    }

    /** 禁止访问 */
    public static <T> ApiResponse<T> forbidden(String message) {
        return new ApiResponse<>(CODE_FORBIDDEN, message, null);
    }

    /** 资源不存在 */
    public static <T> ApiResponse<T> notFound(String message) {
        return new ApiResponse<>(CODE_NOT_FOUND, message, null);
    }

    /** 系统内部错误 */
    public static <T> ApiResponse<T> internalError(String message) {
        return new ApiResponse<>(CODE_INTERNAL_ERROR, message, null);
    }

    // ── getter/setter ───────────────────────────────

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    /**
     * 错误码（串面，如 {@code ECOS-KB-050}）。业务成功/无错误码时为 null（NON_NULL 序列化省略）。
     * 增量 getter（API 只增不改）：暴露已由 {@link #error(int, String, String)} 工厂设定的字段，
     * 使 Jackson 可将串面码一并写入响应体——修复 D.5"错误码进响应体"的内部约定 P0 缺口。
     */
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    /**
     * W09：返回体 traceId。业务代码未显式 set 时，序列化时自动从 MDC 回填，
     * 保证"请求日志 → 响应体"两处同值；MDC 也无值时返回 null（NON_NULL 省略该字段）。
     */
    public String getTraceId() {
        return traceId != null ? traceId : TraceContext.current();
    }

    public void setTraceId(String traceId) { this.traceId = traceId; }

    /** 增量工厂（不改既有静态方法签名，API 只增不改） */
    public ApiResponse<T> withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    // ── 便捷方法 ────────────────────────────────────

    public boolean isSuccess() {
        return this.code == CODE_SUCCESS;
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"code\":-1,\"message\":\"serialization error\"}";
        }
    }

    @Override
    public String toString() {
        return "ApiResponse{code=" + code + ", message='" + message + "'}";
    }
}
