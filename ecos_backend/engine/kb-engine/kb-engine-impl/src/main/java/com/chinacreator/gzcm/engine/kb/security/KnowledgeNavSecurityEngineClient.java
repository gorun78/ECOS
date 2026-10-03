package com.chinacreator.gzcm.engine.kb.security;

import com.chinacreator.gzcm.engine.kb.shared.KbActorSupport;
import com.chinacreator.gzcm.engine.kb.shared.KbAuditPublisher;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 知识导航 (kb-nav) ABAC 客户端 — 走 security-engine REST（与 ontology-engine 同款范式）。
 *
 * <p><b>来源</b>: PMO-A A4 ｜ <b>日期</b>: 2026-09-23 ｜ <b>责任人</b>: fullstack</p>
 *
 * <p><b>继承铁律</b>：架构铁律 §2.4：
 * <ul>
 *   <li>所有写操作 ABAC 裁决（{@code POST /api/v1/security/policy-engine/evaluate}）</li>
 *   <li>不可用 → 默认 DENY（{@code returns false}，不降级放行）</li>
 *   <li>审计事件经 {@link KbAuditPublisher}（F04-15 收口 runtime-event：
 *       EventBusService → 本地 JDBC 兜底 → 双通路全失败即拒绝操作，纠正 K-44/K-45
 *       "warn 后继续 = 静默丢审计"反例）</li>
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
    private final KbAuditPublisher auditPublisher;

    public KnowledgeNavSecurityEngineClient(
            @Value("${service.security.base-url:http://localhost:18081}") String baseUrl,
            @Value("${service.security.timeout-ms:5000}") int timeoutMs,
            @Value("${ecos.nav.tenant-id:ecos}") String tenantId,
            @Autowired(required = false) RestTemplate serviceRestTemplate,
            KbAuditPublisher auditPublisher) {
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        this.tenantId = tenantId;
        this.serviceRestTemplate = serviceRestTemplate;
        this.auditPublisher = auditPublisher;
    }

    /** 本租户标识 —— 由 yml 显式给出（默认 {@code ecos}），禁 {@code "default"} 硬编码（K-41/K-43，F04-16）。 */
    private final String tenantId;

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
                    "userId", KbActorSupport.currentActor(),
                    "tenantId", tenantId,
                    "role", primaryRoleOrDefault()));
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
     * 审计事件 — 经 {@link KbAuditPublisher} 发布（F04-15 收口）。
     * EventBusService 首选 → JDBC 兜底 → 双通路全失败即抛 {@code KbErrorCodeException}
     * （HTTP 503 / KB_022，默认 DENY），<b>纠正 K-44/K-45"warn 后继续 = 静默丢审计"反例</b>。
     *
     * @param action 业务动作（如 {@code kb.category.update}）
     * @param attr   关键属性（如 entityId / userId）
     */
    public void audit(String action, Object attr) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("attr", attr == null ? Map.of() : attr);
        extra.put("resource", "kb-nav");
        // 双通路全失败抛 KbErrorCodeException 上抛给调用方；不再吞掉静默 log.warn
        auditPublisher.emit(action, "nav", KbActorSupport.currentActor(), extra);
    }

    // ── helpers ─────────────────────────────────────────────

    /**
     * 主角色 —— 从 SecurityContext 取第一个 GrantedAuthority，剥离 {@code ROLE_} 前缀。
     * 未认证/无授权 → {@code "anonymous"}（不是 "user" 硬编码，F04-16 K-42/K-43）。
     */
    private String primaryRoleOrDefault() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getAuthorities() != null) {
                for (GrantedAuthority ga : auth.getAuthorities()) {
                    String a = ga == null ? null : ga.getAuthority();
                    if (a == null || a.isBlank() || "ROLE_ANONYMOUS".equals(a)) {
                        continue;
                    }
                    String stripped = a.startsWith("ROLE_") ? a.substring(5) : a;
                    if (!stripped.isBlank()) {
                        return stripped;
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
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

