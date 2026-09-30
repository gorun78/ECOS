package com.chinacreator.gzcm.workspace.security;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
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
 * <p>审计出口：runtime-event {@code EventBusService}（类名反射查找，workspace 编译期
 * 无 runtime-event 依赖）→ kafkaTemplate 反射兜底 → WARN 留痕。</p>
 */
@Component
public class WorkspaceSecurityEngineClient {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceSecurityEngineClient.class);

    private static final String EVENTBUS_IMPL_FQN = "com.chinacreator.gzcm.runtime.eventbus.EventBusService";

    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ApplicationContext applicationContext;

    @Autowired(required = false)
    @Qualifier("kafkaTemplate")
    private Object kafkaTemplateBean;

    public WorkspaceSecurityEngineClient(
            @Value("${service.security.base-url:http://localhost:18081}") String baseUrl,
            @Value("${service.security.timeout-ms:5000}") int timeoutMs,
            ApplicationContext applicationContext) {
        this.baseUrl = baseUrl;
        this.applicationContext = applicationContext;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1000, timeoutMs));
        factory.setReadTimeout(Math.max(1000, timeoutMs));
        this.restTemplate = new RestTemplate(factory);
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
        try {
            Class<?> type = Class.forName(EVENTBUS_IMPL_FQN);
            String[] names = applicationContext.getBeanNamesForType(type);
            if (names.length > 0) {
                Object bus = applicationContext.getBean(names[0]);
                type.getMethod("publish", String.class, Object.class).invoke(bus, KafkaTopics.AUDIT, event);
                return;
            }
        } catch (ClassNotFoundException ignored) {
            // runtime-event 不在 classpath → kafkaTemplate 兜底
        } catch (Exception e) {
            log.debug("WorkspaceSecurityEngineClient: EventBusService 反射发布失败，转兜底: {}", e.getMessage());
        }
        if (kafkaTemplateBean != null) {
            try {
                Method send = kafkaTemplateBean.getClass().getMethod("send", String.class, Object.class);
                send.invoke(kafkaTemplateBean, KafkaTopics.AUDIT, event);
                return;
            } catch (Exception e) {
                log.debug("WorkspaceSecurityEngineClient: kafkaTemplate 兜底失败: {}", e.getMessage());
            }
        }
        log.warn("WorkspaceSecurityEngineClient.audit: EventBus/Kafka 均不可用，审计仅记日志: action={}",
                event.get("action"));
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
        ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + path, entity, Map.class);
        return resp == null ? null : resp.getBody();
    }
}
