package com.chinacreator.gzcm.engine.data.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Pipeline 安全桥接服务 — 收敛 security-engine 强制卡（架构铁律 §2.4）。
 * <p>
 * 三大能力：
 * <ol>
 *   <li>写操作异步审计：{@link #auditWrite(String, String, String)} 通过
 *       {@code POST /api/v1/pipeline→security: /api/security/audit/log} 异步落审计，不阻塞主流程。</li>
 *   <li>敏感字段脱敏：{@link #maskNodeConfig(Map)} 将 SOURCE_JDBC/SOURCE_REST 节点的
 *       password/token 等替换为 {@code ******}，明文仅入库（§4.3）。</li>
 *   <li>ABAC 裁决：{@link #evaluateExecute(String)} 执行前调
 *       {@code POST /api/v1/security/policy-engine/evaluate}，security 不可用时默认 DENY。</li>
 * </ol>
 * 统一经本类 REST 调用 security-engine，禁止各调用点重复实现安全逻辑（§2.4 第 7 条）。
 *
 * <p>P2#4 安全补齐：RestTemplate 显式配置 connect/read 超时（默认 30s/60s），
 * 避免第三方 security-engine 慢响应/不可达导致线程永远挂起。
 *
 * @author DataBridge Datanet Team
 */
@Service
public class PipelineSecurityService {

    private static final Logger log = LoggerFactory.getLogger(PipelineSecurityService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 脱敏占位符 */
    private static final String MASKED = "******";

    /** 敏感字段键名（节点 config 命中即脱敏） */
    private static final Set<String> SENSITIVE_KEYS =
            Set.of("password", "token", "secret", "apikey", "api_key", "authorization", "credential");

    /** P2#4：第三方 security-engine REST 调用的连接超时（毫秒）*/
    private static final int CONNECT_TIMEOUT_MS = 30000;

    /** P2#4：第三方 security-engine REST 调用的读取超时（毫秒）*/
    private static final int READ_TIMEOUT_MS = 60000;

    /** security-engine 基地址（gateway 聚合后走本进程 8080；独立部署时按环境覆盖） */
    @Value("${dw.security.base-url:http://localhost:8080}")
    private String securityBaseUrl;

    /**
     * REST 客户端（P2#4：SimpleClientHttpRequestFactory 显式 30s/60s connect/read 超时，
     * 第三方迟延或不可达时快速失败走默认 DENY/默认脱敏分支）。
     */
    private final RestTemplate restTemplate = newRestTemplateWithTimeouts(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);

    /**
     * 构造带显式超时的 RestTemplate（P2#4）。
     * 抽独立方法便于单测 / 后续切换更复杂的工厂（如 SSL）时 class 字段不动。
     */
    static RestTemplate newRestTemplateWithTimeouts(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1, connectTimeoutMs));
        factory.setReadTimeout(Math.max(1, readTimeoutMs));
        return new RestTemplate(factory);
    }

    // ==================== 1. 写操作异步审计 ====================

    /**
     * 异步写审计日志（不阻塞主流程）。
     *
     * @param action   动作名（如 PIPELINE_CREATE / PIPELINE_UPDATE / PIPELINE_DELETE / PIPELINE_EXECUTE）
     * @param resource 资源 ID（definitionId）
     * @param userId   操作者 ID（可空，默认 system）
     */
    @Async
    public void auditWrite(String action, String resource, String userId) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("userId", userId != null && !userId.isBlank() ? userId : "system");
            body.put("action", action);
            body.put("resource", "pipeline:" + resource);
            body.put("result", "SUCCESS");
            body.put("detail", "action=" + action + ", definitionId=" + resource);
            Object response = restTemplate.postForObject(
                    securityBaseUrl + "/api/security/audit/log", body, Object.class);
            log.debug("Pipeline audit accepted: action={}, resource={}, resp={}", action, resource, response);
        } catch (Exception e) {
            // 审计失败不阻塞主流程，但必须留痕（warn 级）
            log.warn("Pipeline audit write failed (ignored): action={}, resource={}, error={}",
                    action, resource, e.getMessage());
        }
    }

    // ==================== 2. 敏感字段脱敏 ====================

    /**
     * 脱敏节点 config 中的敏感字段（递归处理 map 值）。
     * <p>命中 {@link #SENSITIVE_KEYS} 键名时，将对应字符串值替换为 {@code ******}。
     * 返回新 Map（不修改入参），供 VO 序列化使用。
     *
     * @param raw 原始 config（含明文）
     * @return 脱敏后的 config（同结构），入参为 null 时返回空 Map
     */
    public Map<String, Object> maskNodeConfig(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isSensitiveKey(key) && value != null && !(value instanceof Map)) {
                masked.put(key, MASKED);
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) value;
                masked.put(key, maskNodeConfig(nested));
            } else {
                masked.put(key, value);
            }
        }
        return masked;
    }

    /**
     * 节点 config JSON 字符串 → 脱敏 Map（解析失败返回空 Map）。
     */
    public Map<String, Object> parseAndMaskConfig(String configJson) {
        if (configJson == null || configJson.isBlank() || "{}".equals(configJson.trim())) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> raw = MAPPER.readValue(configJson, new TypeReference<Map<String, Object>>() {});
            return maskNodeConfig(raw);
        } catch (Exception e) {
            log.warn("Failed to parse node config for masking: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private boolean isSensitiveKey(String key) {
        return key != null && SENSITIVE_KEYS.contains(key.toLowerCase());
    }

    // ==================== 3. ABAC 裁决（execute 前置） ====================

    /**
     * 执行前置 ABAC 裁决。
     * <p>调 security-engine {@code POST /api/v1/security/policy-engine/evaluate}，
     * 命中 allow 才放行。security-engine 不可用（连接/调用异常）时默认 DENY（§2.4 第 6 条），
     * 返回 {@code AllowedResult(false, "SECURITY_ENGINE_UNAVAILABLE")}。
     *
     * @param definitionId Pipeline 定义 ID
     * @return 裁决结果
     */
    public AllowedResult evaluateExecute(String definitionId) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("action", "pipeline.execute");
            input.put("resource", "pipeline:" + definitionId);
            input.put("role", "system");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("policy", "rbac");
            body.put("input", input);

            // 用 HttpHeaders 显式声明 JSON Content-Type（避免 RestTemplate 默认 XML 序列化）
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    securityBaseUrl + "/api/v1/security/policy-engine/evaluate",
                    entity, String.class);

            String bodyStr = response.getBody();
            if (bodyStr == null || bodyStr.isEmpty()) {
                log.warn("ABAC evaluate returned empty body: definitionId={}", definitionId);
                return AllowedResult.deny("ABAC_EVALUATE_FAILED");
            }
            // 解析 JSON 响应
            Map<String, Object> apiResp = MAPPER.readValue(bodyStr,
                    new TypeReference<Map<String, Object>>() {});
            if (apiResp == null) {
                log.warn("ABAC evaluate returned null: definitionId={}", definitionId);
                return AllowedResult.deny("ABAC_EVALUATE_FAILED");
            }
            // ApiResponse 包装：业务数据在 "data" 字段内
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) apiResp.get("data");
            if (data == null || data.isEmpty()) {
                // fallback: 若 data 为空（旧版 security-engine 兼容），尝试直接从顶层取
                data = apiResp;
            }
            Object allow = data.get("allow");
            boolean allowed = Boolean.TRUE.equals(allow);
            String reason = data.get("fallback") != null
                    ? data.get("fallback").toString()
                    : (allowed ? "ALLOW" : "DENY");
            if (allowed) {
                log.info("ABAC allow pipeline execute: definitionId={}, reason={}", definitionId, reason);
            } else {
                log.warn("ABAC deny pipeline execute: definitionId={}, reason={}", definitionId, reason);
            }
            return new AllowedResult(allowed, reason);
        } catch (Exception e) {
            // security-engine 不可用 → 默认 DENY（架构铁律 §2.4 第 6 条）
            log.warn("ABAC evaluate failed (default DENY): definitionId={}, error={}", definitionId, e.getMessage());
            return AllowedResult.deny("SECURITY_ENGINE_UNAVAILABLE");
        }
    }

    /**
     * ABAC 裁决结果。
     */
    public record AllowedResult(boolean allowed, String reason) {
        public static AllowedResult deny(String reason) {
            return new AllowedResult(false, reason);
        }

        public static AllowedResult allow(String reason) {
            return new AllowedResult(true, reason);
        }
    }
}
