package com.chinacreator.gzcm.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后端 ApiResponse 信封 → CLI 统一输出契约（详设第 16 篇 §16.4）。
 *
 * <p>顶层固定：{@code ok / status / data / error / trace_id / source_refs}。
 * {@code ok} 只表示调用完成，<b>不等于</b>业务条件满足。</p>
 */
public final class EnvelopeMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_RAW = 8_192;

    private EnvelopeMapper() {
    }

    public static Map<String, Object> map(String operationId, OperationRegistry.Operation op,
                                   HttpTransport.Result result, String requestedTraceId) {
        CliStatus status;
        Object data = null;
        Map<String, Object> error = null;

        if (result.transportFailed()) {
            status = CliStatus.UNAVAILABLE;
            error = error("ECOS-CLI-UNAVAILABLE", "无法连接网关或请求超时",
                    result.failureDetail() == null ? null : result.failureDetail());
        } else {
            JsonNode body = parse(result.body());
            boolean isEnvelope = body != null && body.has("code");
            int http = result.status();

            if (http >= 200 && http < 300) {
                if (isEnvelope) {
                    int code = body.path("code").asInt(-1);
                    if (code == 0) {
                        status = CliStatus.SUCCESS;
                        data = MAPPER.convertValue(body.get("data"), Object.class);
                    } else {
                        status = CliStatus.FAILED;
                        error = error(body.path("errorCode").asText("ECOS-CLI-RESPONSE"),
                                body.path("message").asText("后端返回非零 code"),
                                "code=" + code);
                    }
                } else {
                    status = CliStatus.SUCCESS;
                    data = body != null ? MAPPER.convertValue(body, Object.class) : null;
                }
            } else if (http == 401) {
                status = CliStatus.BLOCKED;
                error = error("ECOS-CLI-AUTH", "未认证或 Token 已过期", "见 --token / ECOS_TOKEN / --profile");
            } else if (http == 403) {
                status = CliStatus.BLOCKED;
                error = error("ECOS-CLI-FORBIDDEN", "服务端准入/权限拒绝（ABAC）", "CLI 本地不得放行");
            } else if (http == 502 || http == 503 || http == 504) {
                status = CliStatus.UNAVAILABLE;
                error = error("ECOS-CLI-DEP-UNAVAILABLE", "依赖真实不可用", "HTTP " + http);
            } else {
                status = CliStatus.FAILED;
                String code = isEnvelope ? body.path("errorCode").asText("ECOS-CLI-HTTP-" + http)
                        : "ECOS-CLI-HTTP-" + http;
                String message = isEnvelope ? body.path("message").asText("请求失败")
                        : "请求失败（无 ApiResponse 信封）";
                error = error(code, message, "HTTP " + http);
            }

            if (isEnvelope) {
                String backendTrace = body.path("traceId").asText(null);
                if (backendTrace != null && !backendTrace.isBlank()) {
                    requestedTraceId = backendTrace;
                }
                if (data == null && body.has("data") && !body.get("data").isNull()) {
                    data = MAPPER.convertValue(body.get("data"), Object.class);
                }
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", status == CliStatus.SUCCESS || status == CliStatus.PARTIAL);
        out.put("status", status.name());
        out.put("data", data);
        out.put("error", error);
        out.put("trace_id", result.responseTraceId() != null ? result.responseTraceId() : requestedTraceId);
        List<Map<String, Object>> refs = new ArrayList<>();
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("operationId", operationId);
        ref.put("path", op.path());
        refs.add(ref);
        out.put("source_refs", refs);
        return out;
    }

    public static Map<String, Object> local(CliStatus status, String code, String message, String hint,
                                     List<Map<String, Object>> sourceRefs, String traceId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", status == CliStatus.SUCCESS || status == CliStatus.PARTIAL);
        out.put("status", status.name());
        out.put("data", null);
        out.put("error", status == CliStatus.SUCCESS ? null : error(code, message, hint));
        out.put("trace_id", traceId);
        out.put("source_refs", sourceRefs == null ? List.of() : sourceRefs);
        return out;
    }

    private static Map<String, Object> error(String code, String message, String hint) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("code", code);
        e.put("message", Redactor.redact(message));
        e.put("hint", hint == null ? null : Redactor.redact(hint));
        return e;
    }

    private static JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            String raw = body.length() > MAX_RAW ? body.substring(0, MAX_RAW) + "…" : body;
            return MAPPER.getNodeFactory().textNode(Redactor.redact(raw));
        }
    }
}
