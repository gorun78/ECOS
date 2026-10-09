package com.chinacreator.gzcm.workspace.security;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * H10-T1 (PMO-74 N7/N16) — workspace 对 security-engine 的统一访问层。
 *
 * <p>铁律 §2.4：RLS / CLS / ABAC 裁决 / 审计一律走 security-engine REST + Kafka，
 * workspace 内不再自建 ABAC 策略解析（原 {@link AbacQueryFilter} 直查
 * td_abac_policy + {@code ${userId}} 字符串拼接的二阶注入路径已废除）；
 * security-engine 不可用时<b>默认 DENY</b>：RLS → "1=0"，CLS → 空集，ABAC → false。</p>
 *
 * <p>卷07 F07-17 底座收口（对齐 ai-engine W89 先例）：
 * <ul>
 *   <li>REST 出口注入 {@code WorkspaceRestClientConfig#workspaceSecurityEngineRest}
 *       共享 Bean（base-url 经 runtime-access ServiceEndpointResolver 两态寻址，
 *       禁自建 RestTemplate / 硬编码 localhost）；</li>
 *   <li>审计出口编译期注入 runtime-event {@link EventBusService}
 *       （原 Class.forName 反射 + kafkaTemplate 反射兜底已删除，铁律 §2.5-7
 *       禁业务侧自建 KafkaTemplate；Bus 未装配 → WARN 留痕，不再兜底）。</li>
 * </ul></p>
 */
@Component
public class WorkspaceSecurityEngineClient {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceSecurityEngineClient.class);

    private final RestTemplate restTemplate;
    private final ObjectProvider<EventBusService> eventBusProvider;

    public WorkspaceSecurityEngineClient(
            @Qualifier("workspaceSecurityEngineRest") RestTemplate restTemplate,
            ObjectProvider<EventBusService> eventBusProvider) {
        this.restTemplate = restTemplate;
        this.eventBusProvider = eventBusProvider;
    }

    // ═══════════════ RLS（默认 DENY "1=0"） ═══════════════

    /**
     * POST /api/v1/security/rls/apply。
     *
     * @return WHERE 片段；无策略=空串；不可用/DENY="1=0"（调用方拼进 WHERE 后天然零行）
     */
    @SuppressWarnings("unchecked")
    public String applyRls(String entityType, String tableName, Map<String, Object> filters) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tableName", tableName);
            body.put("userId", currentUserId());
            body.put("entityType", entityType);
            if (filters != null && !filters.isEmpty()) body.put("filters", filters);
            Map<String, Object> resp = postJson("/api/v1/security/rls/apply", body);
            if (resp == null || !(resp.get("data") instanceof Map)) return "1=0";
            Object whereObj = ((Map<String, Object>) resp.get("data")).get("whereClause");
            return whereObj == null ? "" : whereObj.toString();
        } catch (Exception e) {
            log.warn("WorkspaceSecurityEngineClient.applyRls DENY: entityType={} reason={}",
                    entityType, e.getMessage());
            return "1=0";
        }
    }

    // ═══════════════ CLS（默认 DENY 空集） ═══════════════

    /**
     * POST /api/v1/security/cls/columns。
     *
     * @return 允许列；security 不可用/异常/DENY → List.of()
     */
    @SuppressWarnings("unchecked")
    public List<String> allowedColumns(String entityType, List<String> allColumns) {
        if (allColumns == null || allColumns.isEmpty()) return List.of();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tableName", entityType);
            body.put("userId", currentUserId());
            body.put("allColumns", allColumns);
            Map<String, Object> resp = postJson("/api/v1/security/cls/columns", body);
            if (resp == null || !(resp.get("data") instanceof Map)) return List.of();
            Object allowedObj = ((Map<String, Object>) resp.get("data")).get("allowedColumns");
            if (allowedObj instanceof List<?> list) {
                List<String> result = new ArrayList<>(list.size());
                for (Object o : list) result.add(String.valueOf(o));
                return result;
            }
            return List.of();
        } catch (Exception e) {
            log.warn("WorkspaceSecurityEngineClient.allowedColumns DENY: entityType={} reason={}",
                    entityType, e.getMessage());
            return List.of();
        }
    }

    // ═══════════════ ABAC（默认 DENY false） ═══════════════

    /**
     * POST /api/v1/security/policy-engine/evaluate。
     */
    @SuppressWarnings("unchecked")
    public boolean evaluate(String action, Map<String, Object> attrs) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("policy", "rbac");
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("action", action);
            if (attrs != null) input.putAll(attrs);
            input.put("subject", Map.of("userId", currentUserId(), "role", "user"));
            body.put("input", input);
            Map<String, Object> resp = postJson("/api/v1/security/policy-engine/evaluate", body);
            if (resp == null || !(resp.get("data") instanceof Map)) return false;
            Object result = ((Map<String, Object>) resp.get("data")).get("result");
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("WorkspaceSecurityEngineClient.evaluate DENY: action={} reason={}",
                    action, e.getMessage());
            return false;
        }
    }

    // ═══════════════ 审计 — Kafka ecos.audit ═══════════════

    public void audit(String action, String resource, Object result) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("timestamp", Instant.now().toString());
            event.put("module", "workspace");
            event.put("userId", currentUserId());
            event.put("action", action);
            event.put("resource", resource);
            event.put("result", result == null ? "OK" : String.valueOf(result));
            publishAuditEvent(event);
        } catch (Exception e) {
            log.warn("WorkspaceSecurityEngineClient.audit failed: action={} reason={}",
                    action, e.getMessage());
        }
    }

    private void publishAuditEvent(Map<String, Object> event) {
        EventBusService bus = eventBusProvider.getIfAvailable();
        if (bus == null) {
            log.warn("WorkspaceSecurityEngineClient.audit: EventBusService 未装配，审计仅记日志: action={}",
                    event.get("action"));
            return;
        }
        try {
            bus.publish(KafkaTopics.AUDIT, event);
        } catch (Exception e) {
            log.warn("WorkspaceSecurityEngineClient.audit: EventBus 发布失败: action={} reason={}",
                    event.get("action"), e.getMessage());
        }
    }

    // ═══════════════ helpers ═══════════════

    private String currentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // fail-safe：退化为 anonymous 走最小权限，而非伪造真实主体
            log.debug("WorkspaceSecurityEngineClient: 读取 SecurityContext 失败，主体退化为 anonymous: {}", ignored.getMessage());
        }
        return "anonymous";
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> postJson(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        // path 为相对 Bean rootUri（sysman 基址，F07-17 两态寻址单源）的完整路由
        ResponseEntity<Map> resp = restTemplate.postForEntity(path, entity, Map.class);
        return resp == null ? null : resp.getBody();
    }
}
