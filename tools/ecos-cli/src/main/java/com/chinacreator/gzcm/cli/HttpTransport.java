package com.chinacreator.gzcm.cli;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * HTTP 通道：只允许经 gateway 发请求（baseUrl 默认 :8080）。
 *
 * <p>门禁 {@code CliContractParityTest#noCommandTargetsServicePortDirectly} 扫描本源树，
 * 禁出现任何 service 端口字面量。</p>
 *
 * <p>传输层职责限定为「发出 + 取回 + 重试」，<b>不做</b>业务判断：状态码语义由
 * {@link EnvelopeMapper} 统一映射。</p>
 */
public final class HttpTransport {

    public record Result(int status, String body, String responseTraceId, String failureDetail,
                         Long retryAfterSeconds) {
        public Result(int status, String body, String responseTraceId, String failureDetail) {
            this(status, body, responseTraceId, failureDetail, null);
        }

        boolean transportFailed() {
            return status < 0;
        }
    }

    private final String baseUrl;
    private final String traceId;
    private final long timeoutMs;
    private final HttpClient client;
    private final RetryPolicy retryPolicy;
    private final boolean skipSleep;

    public HttpTransport(String baseUrl, String traceId, long timeoutMs) {
        this(baseUrl, traceId, timeoutMs, RetryPolicy.standard(), false);
    }

    public HttpTransport(String baseUrl, String traceId, long timeoutMs, RetryPolicy retryPolicy,
                         boolean skipSleep) {
        this.baseUrl = baseUrl;
        this.traceId = traceId;
        this.timeoutMs = timeoutMs;
        this.retryPolicy = retryPolicy == null ? RetryPolicy.standard() : retryPolicy;
        this.skipSleep = skipSleep;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 10_000L)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String traceId() {
        return traceId;
    }

    public RetryPolicy retryPolicy() {
        return retryPolicy;
    }

    /** 只读 GET（幂等，可重试）。 */
    public Result get(String pathWithQuery, String token) {
        return exchange("GET", pathWithQuery, token, null, null, true);
    }

    /** 写命令：仅在带幂等键时才允许重试。 */
    public Result write(String method, String pathWithQuery, String token, String jsonBody,
                        String idempotencyKey) {
        boolean idempotent = idempotencyKey != null && !idempotencyKey.isBlank();
        return exchange(method, pathWithQuery, token, jsonBody, idempotencyKey, idempotent);
    }

    private Result exchange(String method, String pathWithQuery, String token, String jsonBody,
                            String idempotencyKey, boolean idempotent) {
        Result result = null;
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            result = attemptOnce(method, pathWithQuery, token, jsonBody, idempotencyKey);
            if (!retryPolicy.shouldRetry(attempt, result, idempotent)) {
                return result;
            }
            retryPolicy.sleep(retryPolicy.delayFor(attempt, result), skipSleep);
        }
        if (result != null && result.transportFailed()) {
            return new Result(result.status(), result.body(), result.responseTraceId(),
                    (result.failureDetail() == null ? "" : result.failureDetail() + "；")
                            + CliErrorCodes.RETRY_EXHAUSTED + "（已尝试 " + retryPolicy.maxAttempts() + " 次）",
                    result.retryAfterSeconds());
        }
        return result == null
                ? new Result(-1, null, null, "未发出请求", null)
                : result;
    }

    private Result attemptOnce(String method, String pathWithQuery, String token, String jsonBody,
                               String idempotencyKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + pathWithQuery))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json")
                .header("X-Client", "cli")
                .header("X-Trace-Id", traceId);
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json; charset=UTF-8");
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpRequest.BodyPublisher publisher = jsonBody == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(jsonBody);
        builder.method(method.toUpperCase(Locale.ROOT), publisher);
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String respTrace = response.headers().firstValue("X-Trace-Id")
                    .or(() -> response.headers().firstValue("X-Request-Id"))
                    .orElse(null);
            return new Result(response.statusCode(), response.body(), respTrace, null,
                    retryAfter(response));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, null, null, "请求被中断", null);
        } catch (Exception e) {
            return new Result(-1, null, null,
                    Redactor.redact(e.getClass().getSimpleName() + ": " + e.getMessage()), null);
        }
    }

    /** Retry-After 只取「秒」形态；HTTP-date 形态返回 null（按退避处理，不猜时区）。 */
    private static Long retryAfter(HttpResponse<String> response) {
        String raw = response.headers().firstValue("Retry-After").orElse(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(raw.trim());
            return seconds >= 0 ? seconds : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** --dry-run 只打印调用计划，不发请求。 */
    public String plannedCall(String method, String pathWithQuery) {
        return method + " " + baseUrl + pathWithQuery;
    }
}
