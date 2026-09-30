package com.chinacreator.gzcm.services.identity.security;

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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * H10-T1 (PMO-74 N1/N2 / 铁律 §2.4) — identity-service 调用入口的 security-engine 强制卡。
 *
 * <p>背景：identity-service 此前 security-engine 集成探针命中 0（§2.4-8）。MfaController /
 * PrivacyController 是 MFA 摘除链与匿名删/导个人数据（N1/N2）的入口，本客户端为其补：</p>
 * <ol>
 *   <li><b>ABAC 裁决</b>（§2.4#4）：{@link #evaluate} 先走同 JVM
 *       {@code OpaPolicyService} bean（gateway fat-JAR 聚合态），缺失时 REST
 *       {@code POST /api/v1/security/policy-engine/evaluate}（透传调用方 Authorization 头）。
 *       🔴 security 不可用 / 异常 / 非明确 ALLOW → <b>默认 DENY</b>（§2.4#6）。</li>
 *   <li><b>审计上报</b>（§2.4#5）：{@link #audit} 优先 runtime-event {@code EventBusService}
 *       → {@code kafkaTemplate} → REST {@code POST /api/security/audit/log}（过渡端点）三级
 *       兜底，topic = {@link KafkaTopics#AUDIT}。</li>
 *   <li><b>响应体脱敏</b>（§2.4#3）：{@link #maskValue} 经 {@code POST /api/security/mask}；
 *       security 不可用时<b>默认打码</b>（宁误脱不漏明文 — fail-closed 的脱敏侧语义）。</li>
 * </ol>
 *
 * <p>主体一律取已认证 token 上下文（{@code UserContext}），不接受客户端自报 userId（N1/N2）；
 * 审计主体同样取 token，不落 "system"/"admin" 硬编码（N17 同类）。</p>
 */
@Component
public class IdentitySecurityEngineClient {

    private static final Logger log = LoggerFactory.getLogger(IdentitySecurityEngineClient.class);

    /** runtime-event 横切审计出口（编译期不依赖，类名反射，参照 AiSecurityEngineClient 先例） */
    private static final String EVENTBUS_FQN = "com.chinacreator.gzcm.runtime.eventbus.EventBusService";
    /** security-engine OPA 门面（gateway 聚合态同 JVM bean 优先） */
    private static final String OPA_SERVICE_FQN = "com.chinacreator.gzcm.engine.security.service.OpaPolicyService";
    /** security-engine 脱敏沙箱服务（同 JVM bean 优先于 REST） */
    private static final String SANDBOX_SERVICE_FQN = "com.chinacreator.gzcm.engine.security.service.SecuritySandboxService";

    /** 脱敏占位符（与 DqSecurityService.MASKED 同语义） */
    public static final String MASKED = "******";

    private final ApplicationContext applicationContext;
    private final String securityBaseUrl;
    private final RestTemplate restTemplate;

    @Autowired(required = false)
    @Qualifier("kafkaTemplate")
    private Object kafkaTemplateBean;

    public IdentitySecurityEngineClient(
            ApplicationContext applicationContext,
            @Value("${ecos.identity.security.base-url:http://localhost:8080}") String securityBaseUrl,
            @Value("${ecos.identity.security.timeout-ms:5000}") int timeoutMs) {
        this.applicationContext = applicationContext;
        this.securityBaseUrl = securityBaseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1000, timeoutMs));
        factory.setReadTimeout(Math.max(1000, timeoutMs));
        this.restTemplate = new RestTemplate(factory);
    }

    // ═══════════════ ABAC 裁决（默认 DENY） ═══════════════

    /**
     * 敏感入口授权裁决。
     *
     * @param subjectUserId 已认证主体（token 上下文取得，禁止客户端自报）
     * @param action        动作名：mfa:setup / mfa:verify / mfa:disable / privacy:export / privacy:delete
     * @return true=ALLOW；security 不可用 / 异常 / 非明确放行 → false（默认 DENY，§2.4#6）
     */
    public boolean evaluate(String subjectUserId, String tenantId, String action, Map<String, Object> attrs) {
        // 1) 同 JVM bean 通道（gateway fat-JAR 聚合态）
        Object opa = findBeanByClassName(OPA_SERVICE_FQN);
        if (opa != null) {
            try {
                Method m = opa.getClass().getMethod("evaluate", String.class, Map.class);
                Object resp = m.invoke(opa, "rbac", buildEvalInput(subjectUserId, tenantId, action, attrs));
                // OpaPolicyService.evaluate 裁决键 = allow（兼容 result 别名）
                if (resp instanceof Map<?, ?> map && isAllow(map)) {
                    return true;
                }
                log.warn("[identity] security-engine 裁决未放行（默认 DENY）: action={}, subject={}, verdict={}",
                        action, subjectUserId,
                        resp instanceof Map<?, ?> mm ? mm.getOrDefault("allow", mm.get("result")) : resp);
                return false;
            } catch (Exception e) {
                log.warn("[identity] OpaPolicyService 调用异常（默认 DENY）: action={}, reason={}",
                        action, rootMessage(e));
                return false;
            }
        }
        // 2) REST 通道（standalone 部署，透传调用方 Authorization 头）
        try {
            Map<String, Object> resp = postJson("/api/v1/security/policy-engine/evaluate",
                    Map.of("policy", "rbac", "input", buildEvalInput(subjectUserId, tenantId, action, attrs)));
            if (resp != null && resp.get("data") instanceof Map<?, ?> data) {
                return isAllow(data);
            }
            log.warn("[identity] security REST 裁决响应异常（默认 DENY）: action={}", action);
            return false;
        } catch (Exception e) {
            log.warn("[identity] security-engine 不可用（bean+REST 均失败），默认 DENY（宁误拒不误放）: action={}, subject={}, reason={}",
                    action, subjectUserId, e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildEvalInput(String subjectUserId, String tenantId, String action,
                                               Map<String, Object> attrs) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("action", action);
        if (attrs != null) input.putAll(attrs);
        input.put("subject", Map.of(
                "userId", subjectUserId == null ? "anonymous" : subjectUserId,
                "tenantId", tenantId == null || tenantId.isBlank() ? "default" : tenantId,
                "role", "user"));
        return input;
    }

    /** 裁决判定：仅显式 true 放行（allow 优先，兼容 result 别名）；其余一律视为 DENY */
    private static boolean isAllow(Map<?, ?> verdictMap) {
        Object v = verdictMap.containsKey("allow") ? verdictMap.get("allow") : verdictMap.get("result");
        return Boolean.TRUE.equals(v);
    }

    // ═══════════════ 审计（Kafka ecos.audit 优先，REST 过渡端点兜底） ═══════════════

    /**
     * 敏感操作留痕（成功与被拒都记）。失败仅 warn，不阻塞主流程（§2.4#5）。
     */
    public void audit(String subjectUserId, String username, String action, String resource, String result) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("timestamp", Instant.now().toString());
            event.put("module", "identity-service");
            event.put("userId", subjectUserId == null ? "anonymous" : subjectUserId);
            event.put("username", username);
            event.put("action", action);
            event.put("resource", resource);
            event.put("result", result == null ? "OK" : result);
            if (publishAuditEvent(event)) {
                return;
            }
            // 3) REST 过渡端点兜底（DqSecurityService 同款 /api/security/audit/log）
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("userId", subjectUserId);
            body.put("username", username);
            body.put("action", action);
            body.put("resource", resource);
            body.put("result", result);
            body.put("detail", "module=identity-service, action=" + action + ", result=" + result);
            postJson("/api/security/audit/log", body);
        } catch (Exception e) {
            log.warn("[identity] 审计上报失败（不阻塞主流程，仅留痕）: action={}, subject={}, reason={}",
                    action, subjectUserId, e.getMessage());
        }
    }

    private boolean publishAuditEvent(Map<String, Object> event) {
        // 1) runtime-event EventBusService（类名反射，避免编译期依赖）
        try {
            Class<?> type = Class.forName(EVENTBUS_FQN);
            String[] names = applicationContext.getBeanNamesForType(type);
            if (names.length > 0) {
                Object bus = applicationContext.getBean(names[0]);
                type.getMethod("publish", String.class, Object.class).invoke(bus, KafkaTopics.AUDIT, event);
                return true;
            }
        } catch (ClassNotFoundException ignored) {
            // runtime-event 不在 classpath → kafkaTemplate 兜底
        } catch (Exception e) {
            log.debug("[identity] EventBusService 反射发布失败，转 kafkaTemplate 兜底: {}", e.getMessage());
        }
        // 2) kafkaTemplate 兜底
        if (kafkaTemplateBean != null) {
            try {
                Method send = kafkaTemplateBean.getClass().getMethod("send", String.class, Object.class);
                send.invoke(kafkaTemplateBean, KafkaTopics.AUDIT, event);
                return true;
            } catch (Exception e) {
                log.debug("[identity] kafkaTemplate 兜底失败: {}", e.getMessage());
            }
        }
        return false;
    }

    // ═══════════════ 响应体脱敏（不可用时默认打码） ═══════════════

    /**
     * 单值脱敏：同 JVM {@code SecuritySandboxService.maskValue} 优先，其次 REST
     * {@code /api/security/mask}；均失败 → 返回 {@link #MASKED}（宁误脱不漏明文，§2.4#3/#6）。
     *
     * @param maskType EMAIL / PHONE / ID_CARD / AMOUNT / SHA256
     */
    public String maskValue(Object value, String maskType) {
        if (value == null || value.toString().isEmpty()) {
            return "";
        }
        String raw = value.toString();
        // 1) 同 JVM bean
        Object sandbox = findBeanByClassName(SANDBOX_SERVICE_FQN);
        if (sandbox != null) {
            try {
                Method m = sandbox.getClass().getMethod("maskValue", String.class, String.class);
                Object masked = m.invoke(sandbox, raw, maskType);
                if (masked != null && !masked.toString().isBlank()) {
                    return masked.toString();
                }
            } catch (Exception e) {
                log.warn("[identity] SecuritySandboxService.maskValue 失败（转 REST）: maskType={}, reason={}",
                        maskType, rootMessage(e));
            }
        }
        // 2) REST
        try {
            Map<String, Object> resp = postJson("/api/security/mask",
                    Map.of("value", raw, "maskType", maskType));
            if (resp != null && resp.get("data") instanceof Map<?, ?> data) {
                Object masked = data.get("masked");
                if (masked != null) {
                    return masked.toString();
                }
            }
            log.warn("[identity] security mask REST 响应异常，默认打码: maskType={}", maskType);
        } catch (Exception e) {
            log.warn("[identity] security-engine 脱敏不可用，默认打码（宁误脱不漏明文）: maskType={}, reason={}",
                    maskType, e.getMessage());
        }
        return MASKED;
    }

    // ═══════════════ 工具 ═══════════════

    private Object findBeanByClassName(String fqn) {
        try {
            Class<?> type = Class.forName(fqn);
            String[] names = applicationContext.getBeanNamesForType(type);
            return names.length == 0 ? null : applicationContext.getBean(names[0]);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            log.warn("[identity] bean [{}] 解析失败: {}", fqn, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> postJson(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String auth = currentAuthorizationHeader();
        if (auth != null) {
            headers.set(HttpHeaders.AUTHORIZATION, auth);
        }
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                securityBaseUrl + path, new HttpEntity<>(body, headers), Map.class);
        return resp == null ? null : resp.getBody();
    }

    /** 透传当前请求的 Authorization 头（standalone REST 通道用；无请求上下文返回 null） */
    private String currentAuthorizationHeader() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                HttpServletRequest req = attrs.getRequest();
                return req.getHeader(HttpHeaders.AUTHORIZATION);
            }
        } catch (Exception ignored) {
            // 非请求线程 → 无 header 可透传
        }
        return null;
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur instanceof InvocationTargetException ite && ite.getTargetException() != null) {
            cur = ite.getTargetException();
        }
        return cur.getMessage() != null ? cur.getMessage() : cur.getClass().getSimpleName();
    }
}
