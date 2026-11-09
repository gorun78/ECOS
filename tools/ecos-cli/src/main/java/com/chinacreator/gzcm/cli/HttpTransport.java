package com.chinacreator.gzcm.cli;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP 通道：只允许经 gateway 发请求（baseUrl 默认 :8080）。
 *
 * <p>门禁 {@code CliContractParityTest#noCommandTargetsServicePortDirectly} 扫描本源树，
 * 禁出现任何 service 端口字面量。</p>
 */
public final class HttpTransport {

    public record Result(int status, String body, String responseTraceId, String failureDetail) {
        boolean transportFailed() {
            return status < 0;
        }
    }

    private final String baseUrl;
    private final String traceId;
    private final long timeoutMs;
    private final HttpClient client;

    public HttpTransport(String baseUrl, String traceId, long timeoutMs) {
        this.baseUrl = baseUrl;
        this.traceId = traceId;
        this.timeoutMs = timeoutMs;
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

    public Result get(String pathWithQuery, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + pathWithQuery))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json")
                .header("X-Client", "cli")
                .header("X-Trace-Id", traceId)
                .GET();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String respTrace = response.headers().firstValue("X-Trace-Id").orElse(null);
            return new Result(response.statusCode(), response.body(), respTrace, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, null, null, "请求被中断");
        } catch (Exception e) {
            return new Result(-1, null, null,
                    Redactor.redact(e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    /** --dry-run 只打印调用计划，不发请求。 */
    public String plannedCall(String method, String pathWithQuery) {
        return method + " " + baseUrl + pathWithQuery;
    }
}
