package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
 * H10-T1 (PMO-74 N8) — ai-engine 对 security-engine 的统一访问层。
 *
 * <p>铁律 §2.4：RLS / CLS / ABAC 裁决 / 审计全部经 security-engine REST + Kafka
 * （{@link KafkaTopics#AUDIT}），AI 引擎内禁止重复实现安全逻辑；
 * security-engine 不可用时<b>默认 DENY</b>（不降级放行）。</p>
 *
 * <p>审计出口：优先 runtime 横切通道 {@code EventBusService}（本模块编译期无
 * runtime-event 依赖，按类名反射查找 bean，参照 sysman AuditAspect 先例），
 * 其次 {@code kafkaTemplate} 反射兜底（与 ontology SecurityEngineClient 同款）。</p>
 *
 * <p>RestTemplate 自构带超时（参照 PipelineSecurityService 先例），不依赖外部 bean，
 * 保证在 gateway / aiming / ai-engine-boot 各进程均可装配。</p>
 */
@Component
public class AiSecurityEngineClient {

    private static final Logger log = LoggerFactory.getLogger(AiSecurityEngineClient.class);

    private static final String EVENTBUS_IMPL_FQN = "com.chinacreator.gzcm.runtime.eventbus.EventBusService";

    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ApplicationContext applicationContext;

    @Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("kafkaTemplate")
    private Object kafkaTemplateBean;

    public AiSecurityEngineClient(
            @Value("${service.security.base-url:http://localhost:18081}") String baseUrl,
            @Value("${service.security.timeout-ms:2000}") int timeoutMs,
            ApplicationContext applicationContext) {
        this.baseUrl = baseUrl;
        this.applicationContext = applicationContext;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1000, timeoutMs));
        factory.setReadTimeout(Math.max(1000, timeoutMs));
        this.restTemplate = new RestTemplate(factory);
    }

    // ═══════════════ ABAC 裁决（默认 DENY） ═══════════════

    /**
     * POST /api/v1/security/policy-engine/evaluate。
     *
     * @return true=允许；security 不可用 / 响应异常 / 明确 DENY → false（默认 DENY）
     */
    @SuppressWarnings("unchecked")
    public boolean evaluate(String userId, String tenantId, String action, Map<String, Object> attrs) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("policy", "rbac");
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("action", action);
            if (attrs != null) input.putAll(attrs);
            input.put("subject", Map.of(
                    "userId", userId == null ? "anonymous" : userId,
                    "tenantId", tenantId == null ? "default" : tenantId,
                    "role", "user"));
            body.put("input", input);
            Map<String, Object> resp = postJson("/api/v1/security/policy-engine/evaluate", body);
            if (resp == null || !(resp.get("data") instanceof Map)) return false;
            Object result = ((Map<String, Object>) resp.get("data")).get("result");
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("AiSecurityEngineClient.evaluate DENY (security 不可用/裁决失败): action={} reason={}",
                    action, e.getMessage());
            return false;
        }
    }

    // ═══════════════ RLS（默认 DENY "1=0"） ═══════════════

    /**
     * POST /api/v1/security/rls/apply — 行级安全 WHERE 片段。
     *
     * @return whereClause；无策略=""；security 不可用/异常/DENY → "1=0"
     */
    @SuppressWarnings("unchecked")
    public String applyRls(String userId, String tenantId, String entityType,
                           String tableName, Map<String, Object> filters) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tableName", tableName);
            body.put("userId", userId == null ? "anonymous" : userId);
            body.put("entityType", entityType);
            if (tenantId != null) body.put("tenantId", tenantId);
            if (filters != null && !filters.isEmpty()) body.put("filters", filters);
            Map<String, Object> resp = postJson("/api/v1/security/rls/apply", body);
            if (resp == null || !(resp.get("data") instanceof Map)) return "1=0";
            Object whereObj = ((Map<String, Object>) resp.get("data")).get("whereClause");
            return whereObj == null ? "" : whereObj.toString();
        } catch (Exception e) {
            log.warn("AiSecurityEngineClient.applyRls DENY: entityType={} reason={}", entityType, e.getMessage());
            return "1=0";
        }
    }

    // ═══════════════ CLS（默认 DENY 空集） ═══════════════

    /**
     * POST /api/v1/security/cls/columns — 列级安全可见列。
     *
     * @return 允许列；security 不可用/异常 → List.of()（DENY-all 语义）
     */
    @SuppressWarnings("unchecked")
    public List<String> allowedColumns(String userId, String tenantId, String entityType, List<String> allColumns) {
        if (allColumns == null || allColumns.isEmpty()) return List.of();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tableName", entityType);
            body.put("userId", userId == null ? "anonymous" : userId);
            if (tenantId != null) body.put("tenantId", tenantId);
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
            log.warn("AiSecurityEngineClient.allowedColumns DENY: entityType={} reason={}",
                    entityType, e.getMessage());
            return List.of();
        }
    }

    // ═══════════════ 审计 — Kafka ecos.audit（runtime 横切出口） ═══════════════

    public void audit(String userId, String action, String resource, Object result) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("timestamp", Instant.now().toString());
            event.put("module", "ai-engine");
            event.put("userId", userId == null ? "anonymous" : userId);
            event.put("action", action);
            event.put("resource", resource);
            event.put("result", result == null ? "OK" : String.valueOf(result));
            publishAuditEvent(event);
        } catch (Exception e) {
            log.warn("AiSecurityEngineClient.audit failed: action={} reason={}", action, e.getMessage());
        }
    }

    private void publishAuditEvent(Map<String, Object> event) {
        // 1) runtime-event EventBusService 横切通道（类名反射查找，避免编译期依赖）
        try {
            Class<?> type = Class.forName(EVENTBUS_IMPL_FQN);
            String[] names = applicationContext.getBeanNamesForType(type);
            if (names.length > 0) {
                Object bus = applicationContext.getBean(names[0]);
                type.getMethod("publish", String.class, Object.class).invoke(bus, KafkaTopics.AUDIT, event);
                return;
            }
        } catch (ClassNotFoundException ignored) {
            // runtime-event 不在 classpath → 走 kafkaTemplate 兜底
        } catch (Exception e) {
            log.debug("AiSecurityEngineClient: EventBusService 反射发布失败，转 kafkaTemplate 兜底: {}", e.getMessage());
        }
        // 2) kafkaTemplate 反射兜底（与 ontology SecurityEngineClient.audit 同款）
        if (kafkaTemplateBean != null) {
            try {
                Method send = kafkaTemplateBean.getClass().getMethod("send", String.class, Object.class);
                send.invoke(kafkaTemplateBean, KafkaTopics.AUDIT, event);
                return;
            } catch (Exception e) {
                log.debug("AiSecurityEngineClient: kafkaTemplate 兜底失败: {}", e.getMessage());
            }
        }
        // 3) 无通道 → WARN 留痕
        log.warn("AiSecurityEngineClient.audit: EventBusService/KafkaTemplate 均不可用，审计仅记日志: action={}",
                event.get("action"));
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
