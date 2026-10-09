package com.chinacreator.gzcm.workspace.audit;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * H10-T2 + 卷07 F07-18（W182 / C-6）—— workspace 审计锚点。
 *
 * <p>审计面（X-11 整改）：</p>
 * <ul>
 *   <li><b>包 scope 扩展</b>：原仅 {@code workspace.controller}，现覆盖
 *       {@code com.chinacreator.gzcm.workspace} 全子包（controller / knowledge /
 *       workbook 盲区清零）；</li>
 *   <li><b>写侧</b>：POST/PUT/<b>PATCH</b>/DELETE 全量（PATCH 为 X-11 补漏）；</li>
 *   <li><b>读侧</b>：不做全 GET 审计（风暴），按 C-6 判定表只审敏感读 ——
 *       {@code /completeness}、{@code /graph}、{@code /runs}、{@code /decisions}、
 *       {@code /actions}、{@code /cognition/*}；场景列表/详情按 subject 每分钟 1 条抽样；</li>
 *   <li><b>载荷</b>：只带 ID 与摘要（action/resource/statusCode/traceId/userId），
 *       禁带结果原文（与分册 06 F06-03 同规则）。</li>
 * </ul>
 *
 * <p>经 runtime 横切通道发 Kafka {@link KafkaTopics#AUDIT}；不自建审计表。
 * F07-17 底座收口：编译期注入 {@link EventBusService}（禁反射），Bus 未装配 → WARN 留痕。</p>
 */
@Component
public class WorkspaceSecurityEngineAuditInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceSecurityEngineAuditInterceptor.class);

    /** F07-18-2：scope 扩到 workspace 全子包（X-11 盲区 = knowledge / workbook）。 */
    private static final String SCOPE_ROOT_PACKAGE = "com.chinacreator.gzcm.workspace";

    private static final String SCENARIO_COLLECTION_PATH = "/api/v1/workspace/scenarios";

    /** C-6 敏感读判定表（全 GET 审计风暴规避：只审列举面）。 */
    private static final Set<String> SENSITIVE_GET_SUFFIXES =
            Set.of("/completeness", "/graph", "/runs", "/decisions", "/actions");

    private static final String COGNITION_PATH_SEGMENT = "/cognition/";

    /** 抽样窗口的 subject 状态上限（F07-21 禁无界内存态；超限即清空重建，最坏丢一轮抽样）。 */
    private static final int SAMPLE_STATE_MAX_SUBJECTS = 4096;

    private final ObjectProvider<EventBusService> eventBusProvider;

    /** subject → 最近一次列表/详情抽样的分钟窗口号。 */
    private final ConcurrentHashMap<String, Long> lastListSampleMinute = new ConcurrentHashMap<>();
    private final AtomicInteger sampleStateSize = new AtomicInteger();

    public WorkspaceSecurityEngineAuditInterceptor(ObjectProvider<EventBusService> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/**");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            if (!(handler instanceof HandlerMethod hm)) return;
            if (!hm.getBeanType().getPackageName().startsWith(SCOPE_ROOT_PACKAGE)) return;
            String httpMethod = request.getMethod();
            String uri = request.getRequestURI();
            boolean write = "POST".equals(httpMethod) || "PUT".equals(httpMethod)
                    || "PATCH".equals(httpMethod) || "DELETE".equals(httpMethod);
            boolean sensitiveGet = "GET".equals(httpMethod) && isSensitiveRead(uri);
            boolean sampledListGet = "GET".equals(httpMethod)
                    && isScenarioListOrDetail(uri) && listSampleAdmitted(resolveUserId(request));
            if (!write && !sensitiveGet && !sampledListGet) return;
            Map<String, Object> event = buildAuditEvent(request, response.getStatus(),
                    ex != null, hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName());
            publishAuditEvent(event);
        } catch (Exception e) {
            log.warn("WorkspaceSecurityEngineAuditInterceptor: 审计锚点事件失败(不阻塞主流程): {}", e.getMessage());
        }
    }

    /** C-6：敏感读端点判定（认知四端点含 /cognition/，其余按路径后缀）。 */
    boolean isSensitiveRead(String uri) {
        if (uri == null) return false;
        if (uri.contains(COGNITION_PATH_SEGMENT)) return true;
        for (String suffix : SENSITIVE_GET_SUFFIXES) {
            if (uri.endsWith(suffix)) return true;
        }
        return false;
    }

    /** 场景列表（/scenarios）与详情（/scenarios/{id}，无更深子路径）。 */
    boolean isScenarioListOrDetail(String uri) {
        if (uri == null) return false;
        String trimmed = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        if (SCENARIO_COLLECTION_PATH.equals(trimmed)) return true;
        if (!trimmed.startsWith(SCENARIO_COLLECTION_PATH + "/")) return false;
        String rest = trimmed.substring(SCENARIO_COLLECTION_PATH.length() + 1);
        return !rest.isEmpty() && rest.indexOf('/') < 0;
    }

    /** 抽样闸门：每 subject 每分钟至多 1 条列表/详情审计（有界状态，见 SAMPLE_STATE_MAX_SUBJECTS）。 */
    boolean listSampleAdmitted(String subject) {
        long minute = System.currentTimeMillis() / 60_000L;
        Long previous = lastListSampleMinute.get(subject);
        if (previous != null && previous == minute) return false;
        if (previous == null && sampleStateSize.incrementAndGet() > SAMPLE_STATE_MAX_SUBJECTS) {
            lastListSampleMinute.clear();
            sampleStateSize.set(1);
        }
        lastListSampleMinute.put(subject, minute);
        return true;
    }

    /**
     * 审计载荷构造 —— F07-21（W185 / X-58 修正）：载荷必须携带与日志/响应/出站同源的
     * {@code traceId}（{@link EcosTraceContext} 单源）。载荷只带 ID 与摘要，禁带结果原文
     * （F07-18-2 / 分册 06 F06-03 同规则）。public 以便 {@code WorkspaceTraceIdTest} 直验。
     */
    public Map<String, Object> buildAuditEvent(HttpServletRequest request, int statusCode,
                                               boolean failed, String resource) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("timestamp", Instant.now().toString());
        event.put("module", "workspace");
        event.put("traceId", EcosTraceContext.currentTraceId());
        event.put("userId", resolveUserId(request));
        event.put("action", request.getMethod() + " " + request.getRequestURI());
        event.put("resource", resource);
        event.put("result", statusCode < 400 && !failed ? "OK" : "FAIL");
        event.put("statusCode", statusCode);
        return event;
    }

    /** 载荷键白名单（F07-18-2 验收：审计载荷只带 ID 与摘要）。 */
    public static List<String> auditPayloadKeyWhitelist() {
        return List.of("eventId", "timestamp", "module", "traceId", "userId",
                "action", "resource", "result", "statusCode");
    }

    private void publishAuditEvent(Map<String, Object> event) {
        EventBusService bus = eventBusProvider.getIfAvailable();
        if (bus == null) {
            log.warn("WorkspaceSecurityEngineAuditInterceptor: EventBusService 未装配，审计仅记日志: action={} userId={}",
                    event.get("action"), event.get("userId"));
            return;
        }
        try {
            bus.publish(KafkaTopics.AUDIT, event);
        } catch (Exception e) {
            log.warn("WorkspaceSecurityEngineAuditInterceptor: EventBus 发布失败: action={} reason={}",
                    event.get("action"), e.getMessage());
        }
    }

    private String resolveUserId(HttpServletRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Throwable ignored) {
            // spring-security 不在 classpath
        }
        String header = request.getHeader("X-User-Id");
        if (header != null && !header.isBlank()) return header;
        return "anonymous";
    }
}
