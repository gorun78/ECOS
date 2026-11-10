package com.chinacreator.gzcm.engine.ai.copilot;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.chinacreator.gzcm.engine.ai.security.ServicePrincipalTrustHeaders;
import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Copilot 工具共享底座：属主寻址 + 调用方凭据中继 + 结果序列化/截断 + 受控执行确认门。
 *
 * <p><b>凭据中继</b>：工具的 HTTP 调用必须携带当前调用者身份（{@link CallerCredentialSource}），
 * 否则经 gateway :8080 的属主端点一律 401/403；缺失凭据时<b>不加头</b>并让属主如实拒绝，
 * 禁自造服务账号（与 F02-15/X-35 同纪律）。</p>
 *
 * <p><b>受控执行</b>：T3 工具（评估计算/管道运行/预测 run）要求入参 {@code confirm=true}
 * 才真正调用；否则返回「需用户确认」的失败结果，由模型向用户复述确认。</p>
 */
@Component
public class CopilotToolSupport {

    private static final Logger log = LoggerFactory.getLogger(CopilotToolSupport.class);

    private static final int MAX_RESULT_CHARS = 3000;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ObjectProvider<ServiceEndpointResolver> endpointProvider;
    private final RestTemplate restTemplate;

    public CopilotToolSupport(ObjectProvider<ServiceEndpointResolver> endpointProvider) {
        this.endpointProvider = endpointProvider;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(8000);
        this.restTemplate = new RestTemplate(factory);
        this.restTemplate.getInterceptors().add((request, body, execution) -> {
            // 调用方凭据逐字中继 + 被网关剥离的信任头还原（缺失即不发头，属主侧如实拒绝）
            ServicePrincipalTrustHeaders.apply(request.getHeaders());
            return execution.execute(request, body);
        });
    }

    /** 属主基址（route-manifest 双模寻址）；解析不到即抛异常（禁回落硬编码地址）。 */
    public String base(String owner, ServiceEndpointResolver.RouteMode mode) {
        ServiceEndpointResolver resolver = endpointProvider == null ? null : endpointProvider.getIfAvailable();
        if (resolver == null) {
            throw new IllegalStateException("ServiceEndpointResolver 未装配（owner=" + owner + "）");
        }
        String host = resolver.resolve(owner, mode)
                .orElseThrow(() -> new IllegalStateException("属主基址不可解析（owner=" + owner + "）"));
        return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    }

    public Object get(String owner, ServiceEndpointResolver.RouteMode mode, String path,
                      Map<String, Object> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(base(owner, mode) + path);
        if (query != null) {
            query.forEach((key, value) -> {
                if (value != null && !String.valueOf(value).isBlank()) {
                    builder.queryParam(key, value);
                }
            });
        }
        URI uri = builder.build().encode().toUri();
        return restTemplate.getForObject(uri, Map.class);
    }

    public Object post(String owner, ServiceEndpointResolver.RouteMode mode, String path, Object body) {
        URI uri = URI.create(base(owner, mode) + path);
        return restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(body), Map.class).getBody();
    }

    public Object put(String owner, ServiceEndpointResolver.RouteMode mode, String path, Object body) {
        URI uri = URI.create(base(owner, mode) + path);
        return restTemplate.exchange(uri, HttpMethod.PUT, new HttpEntity<>(body), Map.class).getBody();
    }

    /** 受控执行确认门：T3 工具必须显式 {@code confirm=true}。 */
    public boolean confirmed(Map<String, Object> args) {
        Object value = args == null ? null : args.get("confirm");
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value))
                || "1".equals(String.valueOf(value));
    }

    public ToolExecutorService.ToolResult confirmRequired(String toolName) {
        return fail(toolName, "该操作属于受控执行：请先向用户复述变更内容，用户确认后在入参携带 confirm=true 重试",
                0L);
    }

    public ToolExecutorService.ToolResult ok(String toolName, Object result, long elapsedMs) {
        String content = toJson(result);
        if (content.length() > MAX_RESULT_CHARS) {
            content = content.substring(0, MAX_RESULT_CHARS) + "...[truncated]";
        }
        String callId = "cop_" + UUID.randomUUID().toString().substring(0, 8);
        return ToolExecutorService.ToolResult.ok(callId, toolName, content, elapsedMs);
    }

    public ToolExecutorService.ToolResult fail(String toolName, String reason, long elapsedMs) {
        String callId = "cop_" + UUID.randomUUID().toString().substring(0, 8);
        log.warn("[CopilotTools] {} 失败: {}", toolName, reason);
        return ToolExecutorService.ToolResult.fail(callId, toolName, reason, elapsedMs);
    }

    public String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /** 通用执行壳：计时 + 异常→失败（startMs 用 {@code System.currentTimeMillis()}）。 */
    public ToolExecutorService.ToolResult execute(String toolName, long startMs, ThrowingSupplier supplier) {
        try {
            Object result = supplier.get();
            return ok(toolName, result, Math.max(System.currentTimeMillis() - startMs, 0L));
        } catch (RuntimeException e) {
            return fail(toolName, "引擎调用失败: " + e.getMessage(),
                    Math.max(System.currentTimeMillis() - startMs, 0L));
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier {
        Object get();
    }

    // ── 小工具 ─────────────────────────────────────────────────────────

    public static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static int intOr(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            target.put(key, value);
        }
    }

    public static Map<String, Object> query(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            putIfPresent(map, String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    public static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 取响应 data 段（兼容 ApiResponse 信封）；无信封时原样返回。 */
    public static Object data(Object envelope) {
        if (envelope instanceof Map<?, ?> map && map.containsKey("data")) {
            return map.get("data");
        }
        return envelope;
    }

    /** 可选参数：仅在 args 含键时取值。 */
    public static Object opt(Map<String, Object> args, String key) {
        return args == null ? null : args.get(key);
    }

    public static String requireText(Map<String, Object> args, String key) {
        String value = str(args == null ? null : args.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必填参数 " + key);
        }
        return value;
    }

    public static String idempotencyKey(Map<String, Object> args, String toolName, String sessionId) {
        String explicit = str(opt(args, "idempotencyKey"));
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        String seed = (sessionId == null ? "" : sessionId) + "|" + toolName + "|" + JSON.valueToTree(args);
        return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    public static ServiceEndpointResolver.RouteMode mode(String value) {
        return "SERVICE".equalsIgnoreCase(value)
                ? ServiceEndpointResolver.RouteMode.SERVICE
                : ServiceEndpointResolver.RouteMode.MONOLITH;
    }
}
