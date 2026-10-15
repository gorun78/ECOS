package com.chinacreator.gzcm.workspace.security;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;
import com.chinacreator.gzcm.common.security.header.ServiceContext;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
 * security-engine <b>拒绝</b>时默认 DENY：RLS → "1=0"，CLS → 空集，ABAC → false。</p>
 *
 * <p><b>不可用与拒绝分态（详设-01 §4.5.4 六×四降级矩阵 / §4.7.1 三通道全 DENY 门禁，
 * 验收载体 {@code SecurityDownAllChannelDenyTest#page} / {@code #export}）</b>：
 * 连不上 / 读超时 / security 侧 5xx 属<b>可用性故障</b>而非策略裁决，
 * 由 {@link #postJson} 单点转 {@link SecurityEngineUnavailableException}
 * （HTTP 503 / ECOS-SEC-501）上抛，禁再折叠成 {@code "1=0"} / 空列集 / {@code false} ——
 * 旧形态把"安全引擎故障"渲染成"你没有数据"（200 + 空列表），
 * 违从零实施版 line 2602「503 DENY 且禁渲染空列表冒充无数据」。
 * 4xx（含默认 DENY 的 403）与响应缺段不可解析仍按既有兜底走 DENY 语义，分态不外溢。</p>
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
     * @return WHERE 片段；无策略=空串；security 明确 DENY="1=0"（调用方拼进 WHERE 后天然零行）
     * @throws SecurityEngineUnavailableException security 进程不可用（连不上/超时/5xx），
     *         由 {@link #postJson} 单点转 503，禁冒充"无数据"
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
        } catch (SecurityEngineUnavailableException e) {
            throw e; // 可用性故障保持独立态上抛（§4.5.4 分态）
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
     * @return 允许列；security 明确 DENY/异常 → List.of()
     * @throws SecurityEngineUnavailableException security 进程不可用（连不上/超时/5xx）
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
        } catch (SecurityEngineUnavailableException e) {
            throw e; // 可用性故障不得冒充"无可见列"（§4.5.4 分态）
        } catch (Exception e) {
            log.warn("WorkspaceSecurityEngineClient.allowedColumns DENY: entityType={} reason={}",
                    entityType, e.getMessage());
            return List.of();
        }
    }

    // ═══════════════ ABAC（默认 DENY false） ═══════════════

    /**
     * POST /api/v1/security/policy-engine/evaluate。
     *
     * <p>导出通道（{@code audit:export}）咽喉：security 明确 DENY → {@code false}
     * （调用方转 403）；进程不可用 → 503 {@link SecurityEngineUnavailableException} 上抛，
     * 由 {@code SecurityDownAllChannelDenyTest#export} 钉住"拒绝产出任何字节"。</p>
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
        } catch (SecurityEngineUnavailableException e) {
            throw e; // 可用性故障不得冒充"策略拒绝"（§4.5.4 分态）
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
            // fail-safe：退化为信任链上下文（ADR-7），再退 anonymous 走最小权限，而非伪造真实主体
            log.debug("WorkspaceSecurityEngineClient: 读取 SecurityContext 失败，退化 ServiceContext: {}",
                    ignored.getMessage());
        }
        // workspace 独立进程（:18090）无 Spring Security 上下文，主体由
        // HeaderAuthInterceptor 还原进 ServiceContext（X-ECOS-USER 信任头单源）。
        ServiceContext ctx = ServiceContext.current();
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            return ctx.getUserId();
        }
        return "anonymous";
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> postJson(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        relayCallerCredentials(headers);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp;
        try {
            // path 为相对 Bean rootUri（sysman 基址，F07-17 两态寻址单源）的完整路由
            resp = restTemplate.postForEntity(path, entity, Map.class);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            // 连不上 / 超时 / security 侧 5xx = 可用性故障，不是策略裁决（详设-01 §4.5.4）：
            // 单点转 503 ECOS-SEC-501 上抛，禁在此折叠成 "1=0" / 空列集 / false
            throw new SecurityEngineUnavailableException(path, e.getMessage(), e);
        }
        return resp == null ? null : resp.getBody();
    }

    /**
     * 服务间凭证透传（H10 服务凭证收口前的 ADR-7 信任链唯一合法通道）。
     *
     * <p>security-engine 的 RLS/CLS/ABAC 控制面不在
     * {@code AnonymousEndpointRegistry} ANONYMOUS 面（{@code /api/v1/security} 为
     * CLEARANCE_EXEMPT：需认证、跳过准入裁决）。2026-10-11 live 实证：本客户端
     * 原样出站不带任何凭证 → sysman/gateway Spring Security 默认 DENY 403 →
     * {@code applyRls} 折叠成 "1=0" → {@link ScenarioReadGuard} fail-closed →
     * 场景域 L1/L2 对已认证 admin 也 403（误 DENY，非策略 DENY）。</p>
     *
     * <p>整改：入口请求携带的 {@code Authorization}（用户 JWT 原样中继，security 端
     * JwtAuthenticationFilter 真实验签）与 {@code X-ECOS-USER/ROLE/TENANT/DOMAIN}
     * 信任头逐字转发；无入口请求上下文（异步/定时任务）时不伪造凭证 ——
     * 保持 fail-closed 语义不变，只修"把合法已认证用户误判为不可用"这一 plumbing 缺口。</p>
     */
    private void relayCallerCredentials(HttpHeaders headers) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return; // 无请求上下文：不带凭证，由 security-engine 默认 DENY（宁 DENY 不超发）
        }
        HttpServletRequest request = servletAttrs.getRequest();
        String authorization = trimToNull(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (authorization != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        copyIfPresent(request, headers, HeaderAuthInterceptor.H_USER);
        copyIfPresent(request, headers, HeaderAuthInterceptor.H_ROLE);
        copyIfPresent(request, headers, HeaderAuthInterceptor.H_TENANT);
        copyIfPresent(request, headers, HeaderAuthInterceptor.H_DOMAIN);
    }

    private static void copyIfPresent(HttpServletRequest request, HttpHeaders headers, String header) {
        String value = trimToNull(request.getHeader(header));
        if (value != null) {
            headers.set(header, value);
        }
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
