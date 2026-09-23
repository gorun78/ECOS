package com.chinacreator.gzcm.engine.kb.security;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 知识导航 (kb-nav) ABAC 客户端 — 走 security-engine REST（与 ontology-engine 同款范式）。
 *
 * <p><b>来源</b>: PMO-A A4 ｜ <b>日期</b>: 2026-09-23 ｜ <b>责任人</b>: fullstack</p>
 *
 * <p><b>继承铁律</b>：架构铁律 §2.4：
 * <ul>
 *   <li>所有写操作 ABAC 裁决（{@code POST /api/v1/security/policy-engine/evaluate}）</li>
 *   <li>不可用 → 默认 DENY（{@code returns false}，不降级放行）</li>
 *   <li>审计事件发 Kafka {@link KafkaTopics#AUDIT}（失败仅 log.warn，不阻塞业务）</li>
 * </ul>
 *
 * <p>不直接负责 RLS/CLS/脱敏（kb-nav 是目录/标签维度，无敏感列，不涉及脱敏）。
 * 本类仅承载「操作授权」+「审计」两个动作。</p>
 */
@Component
public class KnowledgeNavSecurityEngineClient {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeNavSecurityEngineClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final int timeoutMs;
    private final RestTemplate serviceRestTemplate;

    /** 可选 — 容器中存在 KafkaTemplate Bean 时注入（required=false 保证缺失时静默 null）。 */
    @Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("kafkaTemplate")
    private Object kafkaTemplateBean;

    public KnowledgeNavSecurityEngineClient(
            @Value("${service.security.base-url:http://localhost:18081}") String baseUrl,
            @Value("${service.security.timeout-ms:5000}") int timeoutMs,
            @Autowired(required = false) RestTemplate serviceRestTemplate) {
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        this.serviceRestTemplate = serviceRestTemplate;
    }

    /**
     * ABAC 裁决 — 调 security-engine 的 OPA evaluate。
     * <p>不可用（无 RestTemplate / 网络异常 / 5xx）一律 DENY（铁律 §2.4-6）。</p>
     *
     * @param action 业务动作标识（如 {@code kb.category.create}）
     * @param attrs  附加属性（可空；会 flat 合并到 input）
     * @return 允许=true；拒绝或不可用=false
     */
    @SuppressWarnings("unchecked")
    public boolean evaluatePolicy(String action, Map<String, Object> attrs) {
        if (serviceRestTemplate == null) {
            log.debug("KNavSecurityEngineClient.evaluatePolicy DENY (no RestTemplate): action={}", action);
            return false;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("policy", "rbac");
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("action", action);
            if (attrs != null) {
                input.putAll(attrs);
            }
            input.put("subject", Map.of(
                    "userId", currentUserIdOrAnonymous(),
                    "tenantId", "default",
                    "role", "user"));
            body.put("input", input);
            Map<String, Object> resp = postJson("/api/v1/security/policy-engine/evaluate", body);
            if (resp == null || resp.get("data") == null) {
                return false;
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map)) {
                return false;
            }
            Object result = ((Map<String, Object>) dataObj).get("result");
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("KNavSecurityEngineClient.evaluatePolicy DENY: action={} reason={}",
                    action, e.getMessage());
            return false;
        }
    }

    /**
     * 审计事件 — 发 Kafka {@link KafkaTopics#AUDIT} topic（不阻塞主流程，失败仅 warn）。
     *
     * @param action 业务动作（如 {@code kb.category.update}）
     * @param attr   关键属性（如 entityId / userId）
     */
    public void audit(String action, Object attr) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("timestamp", System.currentTimeMillis());
            event.put("userId", currentUserIdOrAnonymous());
            event.put("action", action);
            event.put("resource", "kb-nav");
            event.put("attr", attr == null ? Map.of() : attr);
            if (kafkaTemplateBean == null) {
                log.warn("KNavSecurityEngineClient.audit: KafkaTemplate 不可用，仅日志: action={}", action);
                return;
            }
            // 通过 Object 反射调用 send，避免硬依赖 KafkaTemplate 类型（与 ontology 同款，兼容缺失时 classpath）
            java.lang.reflect.Method send = kafkaTemplateBean.getClass().getMethod("send", String.class, Object.class);
            Object future = send.invoke(kafkaTemplateBean, KafkaTopics.AUDIT, event);
            if (future instanceof java.util.concurrent.CompletableFuture<?> cf) {
                cf.whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.warn("KNavSecurityEngineClient.audit async fail: action={} reason={}",
                                action, ex.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("KNavSecurityEngineClient.audit failed: action={} reason={}",
                    action, e.getMessage());
        }
    }

    // ── helpers ─────────────────────────────────────────────

    private String currentUserIdOrAnonymous() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                Object principal = auth.getPrincipal();
                if (principal instanceof String s) {
                    return s;
                }
                return auth.getName();
            }
        } catch (Exception ignored) {
            // 客户端解析失败返回 anonymous，不抛
        }
        return "anonymous";
    }

    private Map<String, Object> postJson(String path, Map<String, Object> body) {
        if (serviceRestTemplate == null) {
            return null;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = serviceRestTemplate.postForEntity(baseUrl + path, entity, Map.class);
        return resp == null ? null : resp.getBody();
    }
}
