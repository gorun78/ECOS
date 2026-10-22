package com.chinacreator.gzcm.common.base;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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
    /** 系统内部错误（信封 v1 约定：{@code code < 0} ＝系统错误；保留不改，见 #142/B4-3e-27 裁决） */
    public static final int CODE_INTERNAL_ERROR = -1;
    /**
     * 服务端错误且要与 HTTP 状态行<b>同号</b>时使用（§3.2.7 5xx 透传＋#142/B4-3e-26 同号判据）。
     * <p>与 {@link #CODE_INTERNAL_ERROR} 的分工：-1 是信封 v1 的「系统错误」负数码约定，
     * 500 是与状态行同号的携带值；新出口（带机读码重载）一律取本常量，旧 -1 面由棘轮门只减不增追缴。</p>
     */
    public static final int CODE_SERVER_ERROR = 500;

    // ── 字段 ────────────────────────────────────────
    private int code;
    private String errorCode;
    private String message;
    private T data;
    private Long timestamp;

    /**
     * 详细设计-00 W09 / C.5.1：全链 traceId（入口由 RequestContextFilter 生成/透传，
     * {@code X-Request-Id} → MDC → 响应体）。
     * <p>增量字段：新增不删除，`@JsonInclude(NON_NULL)` 保证未赋值时 JSON 不序列化
     * `traceId`，与既有 5 字段序列化结果完全一致。</p>
     */
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

    // ── traceId 填充 ────────────────────────────────

    /**
     * W09 工厂：从当前 MDC 读取 traceId 并填充到本响应。
     * <p>调用点 = GlobalExceptionHandler / 各 Controller 出口 / filter 内 4xx 返回。
     * 若 MDC 未设置（异步线程或 actuator-only 路径）保持 null，序列化时被 NON_NULL 忽略。</p>
     */
    public ApiResponse<T> withTraceIdFromMdc() {
        try {
            String tid = org.slf4j.MDC.get("traceId");
            if (tid != null && !tid.isBlank()) this.traceId = tid;
        } catch (Exception ignored) { }
        return this;
    }

    /** W09 工厂：显式指定 traceId（用于单测）。 */
    public ApiResponse<T> withTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            this.traceId = traceId.trim();
        }
        return this;
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

    // ── 带机读码重载（#142/B4-3e-27 收口差距 D-B4-65，只增不改）─────────
    /*
     * <p><b>本批缺陷的成因</b>：上面五枚单参工厂走的是三参私有构造器，<b>从不填</b>
     * {@code errorCode}；本类带 {@code @JsonInclude(NON_NULL)} ⇒ 机读码键在 JSON 面
     * <b>整个不出现</b>（不是空串、不是 null 键，是压根没有这个字段）。余面有两种口径，
     * 登记时必须写清用哪一把尺：粗口径（{@code grep} 直扫 {@code src/main/java}，含注释与
     * javadoc 里的提及，且把带码重载也算进调用串）＝本批改前 <b>946 处／152 文件</b>；
     * 门禁口径（先经 {@code SourceCommentStripper} 剥注释、再用带否定预查的判据只认
     * <b>单参</b>调用）＝改前 <b>945 处／151 文件</b>，本批把 gateway 入口六枚腿迁到带码
     * 重载后 <b>939 处／151 文件</b>（分枚 badRequest 330／internalError 317／notFound 255／
     * forbidden 24／unauthorized 13）。棘轮门钉的是<b>门禁口径</b>；含 test 面粗口径读数
     * 954 处／155 文件，仅作规模参考，不入判据。这与 D-B4-55 是同一坑的
     * 两半：55 关的是「把 {@code String.valueOf(int)} 当机读码填进去」，本条是「干脆没填」。</p>
     *
     * <p><b>为何不五枚工厂一次性补码</b>（裁决依据）：{@code ECOS-*} 码表按域归属
     * （§3.2.7「域内业务错误码仍归各域码表，禁把域错误塞进本族以绕过归属」），而调用点里
     * 大量「资源不存在／参数错误」是<b>域内业务语义</b>，由信封类替它们统一指定一个平台码
     * ＝用批量替换冒充归因，正是本台账反复登记的折叠面成因（D-B4-44／55／58）。故本批
     * 只提供带码通道＋把入口面（gateway {@code GlobalExceptionHandler}）迁过来，
     * 余面由 {@code NoMachineCodeFactoryFaceRatchetTest} 按实跑基线<b>只减不增</b>追缴。</p>
     *
     * <p><b>形参顺序</b>取 {@code (errorCode, message)}，与既有
     * {@link #error(int, String, String)} 里 {@code errorCode} 在 {@code message} 之前同形。
     * 重载不校验码值形态：形态判据（禁数字串冒充机读码）已由
     * {@code ErrorCodeMachineCodeSingleSourceTest} 单源持有，在构造期抛异常会把一条 4xx
     * 兜底腿变成 5xx 面，属新的折叠风险。</p>
     */

    /** 业务错误（带机读码，§3.2.7 码表取登记值） */
    public static <T> ApiResponse<T> badRequest(String errorCode, String message) {
        return new ApiResponse<>(CODE_BAD_REQUEST, errorCode, message, null);
    }

    /** 未认证（带机读码） */
    public static <T> ApiResponse<T> unauthorized(String errorCode, String message) {
        return new ApiResponse<>(CODE_UNAUTHORIZED, errorCode, message, null);
    }

    /** 无权限（带机读码） */
    public static <T> ApiResponse<T> forbidden(String errorCode, String message) {
        return new ApiResponse<>(CODE_FORBIDDEN, errorCode, message, null);
    }

    /** 资源不存在（带机读码） */
    public static <T> ApiResponse<T> notFound(String errorCode, String message) {
        return new ApiResponse<>(CODE_NOT_FOUND, errorCode, message, null);
    }

    /**
     * 系统内部错误（带机读码）。
     * <p><b>{@code code} 取 500 而非 {@link #CODE_INTERNAL_ERROR}</b>（本批裁决）：§3.2.7
     * 要求 5xx 透传、#142/B4-3e-15 已把状态行保真做实，而带码通道是给「已知是服务端错误」
     * 的出口用的——此时把包体数字码写成 -1 会造出「状态行 500／包体 -1」的新分叉，
     * 与 #142/B4-3e-26 为 NPE/ISE 两腿钉下的「{@code code} 与状态行同号」判据同型。
     * 旧单参 {@link #internalError(String)} 的 -1 面属信封类 javadoc 自 v1 定义的
     * 「{@code code < 0} ＝系统错误」约定，改它要连带动 317 处存量语义面＋前端
     * {@code code < 0} 分支，属另批裁决，本批不动、由棘轮门只减不增追缴。</p>
     */
    public static <T> ApiResponse<T> internalError(String errorCode, String message) {
        return new ApiResponse<>(CODE_SERVER_ERROR, errorCode, message, null);
    }

    // ── getter/setter ───────────────────────────────

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

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
