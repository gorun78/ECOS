package com.chinacreator.gzcm.workspace.audit;

import com.chinacreator.gzcm.common.context.TraceContext;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * H10-T2 + F07-18 / C164 — workspace 高危写 + 敏感读审计锚点。
 *
 * <p>覆盖 {@code com.chinacreator.gzcm.workspace} 全子包（controller / knowledge / workbook /
 * query 盲区，X-11）：</p>
 * <ul>
 *   <li><b>写侧（POST/PUT/DELETE）</b>：全量审计（既有行为，扩展到盲区子包）；</li>
 *   <li><b>读侧（GET）</b>：仅 <b>F07-18 / C-6 敏感读</b>审计——场景域 {@code /completeness}·
 *       {@code /graph}·{@code /runs}·认知四端点·决策读·列表·详情；{@code /binding-catalog} 等
 *       静态元数据<b>不</b>审计（避免全 GET 审计风暴）。判定表见设计卷 C-6。</li>
 * </ul>
 *
 * <p><b>E-5 载荷红线</b>：读侧审计载荷只带 <b>ID + 分类 + subject + traceId + 状态</b>，
 * <b>禁带结果原文/行数/coverage 数值</b>（那些需读响应体，与"禁带结果原文"同规则冲突——
 * C-6 表列的 coverage/条数字段需 body 捕获，本拦截器不捕获 body，故只留 ID 级摘要，与
 * 分册 06 F06-03 摘要红线同口径）。afterCompletion 经 runtime 横切通道发 Kafka
 * {@link KafkaTopics#AUDIT}（EventBusService 类名反射优先 → kafkaTemplate 兜底 → WARN
 * 留痕）。不自建审计表、不各自封装通道。</p>
 */
@Component
public class WorkspaceSecurityEngineAuditInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceSecurityEngineAuditInterceptor.class);

    /**
     * F07-18 审计域：workspace 全子包（controller/knowledge/workbook/query 盲区，X-11）。
     * 由原先仅 {@code ...workspace.controller} 收窄面改为根包前缀，知识/工作台盲区写入纳入审计。
     */
    private static final String SCOPE_PACKAGE = "com.chinacreator.gzcm.workspace";

    private static final String EVENTBUS_IMPL_FQN = "com.chinacreator.gzcm.runtime.eventbus.EventBusService";

    /** 场景读端点基址前缀（R-29：仅场景域读，禁把 36 条非场景对象域端点卷入敏感读审计）。 */
    private static final String SCENARIO_WS_PREFIX = "/api/v1/workspace/scenarios";
    private static final String SCENARIO_BIZ_PREFIX = "/api/v1/business/scenarios";
    /** C-6 表明确 ❌ 的静态元数据读（不审计）。 */
    private static final String CATALOG_SUFFIX = "/binding-catalog";

    private final ApplicationContext applicationContext;

    @Autowired(required = false)
    @Qualifier("kafkaTemplate")
    private Object kafkaTemplateBean;

    public WorkspaceSecurityEngineAuditInterceptor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
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
            if (!inAuditScope(hm.getBeanType().getPackageName())) return;
            String httpMethod = request.getMethod();
            String uri = request.getRequestURI();
            boolean ok = response.getStatus() < 400 && ex == null;
            String userId = resolveUserId(request);
            String className = hm.getBeanType().getSimpleName();
            String methodName = hm.getMethod().getName();

            Map<String, Object> event;
            if (isWriteMethod(httpMethod)) {
                // 写侧全量（保持既有字段语义，扩展到盲区子包）
                event = buildEvent("write", className, methodName, httpMethod, uri, userId,
                        response.getStatus(), ok, null);
            } else if ("GET".equals(httpMethod)) {
                String category = sensitiveReadCategory(uri);
                if (category == null) return; // 非敏感读（含 /binding-catalog 与对象域读）→ 不审计
                event = buildEvent(category, className, methodName, httpMethod, uri, userId,
                        response.getStatus(), ok, uri);
            } else {
                return;
            }
            emitAudit(event);
        } catch (Exception e) {
            log.warn("WorkspaceSecurityEngineAuditInterceptor: 审计锚点事件失败(不阻塞主流程): {}", e.getMessage());
        }
    }

    // ─────────────── 纯判定/构建（离线可单测，无 servlet 依赖） ───────────────

    /** 审计域判定：workspace 根包及其全部子包（含盲区 knowledge/workbook/query）。 */
    static boolean inAuditScope(String pkg) {
        return pkg != null && (pkg.equals(SCOPE_PACKAGE) || pkg.startsWith(SCOPE_PACKAGE + "."));
    }

    /** 写侧方法（F07-18：CUD 全量审计）。 */
    static boolean isWriteMethod(String m) {
        return "POST".equals(m) || "PUT".equals(m) || "DELETE".equals(m);
    }

    /**
     * C-6 敏感读分类：仅返回<b>场景域</b>下的敏感读分类 token；非敏感（静态元数据 / 对象域 /
     * 健康检查 / 写方法）返回 {@code null}。<b>R-29 边界</b>：只按场景域基址前缀判，object 域
     * {@code /objects/...}·{@code /api/query} 等非场景读不归类（防把 36 条非场景读审计卷入）。
     */
    static String sensitiveReadCategory(String uri) {
        if (uri == null || uri.isBlank()) return null;
        String path = uri;
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        path = path.trim();
        if (path.isEmpty()) return null;

        // C-6 明确 ❌：静态元数据读不审计（置于前缀判定前，兜住 /api 形态）
        if (path.endsWith(CATALOG_SUFFIX)) return null;

        boolean scenarioDomain =
                path.startsWith(SCENARIO_WS_PREFIX) || path.startsWith(SCENARIO_BIZ_PREFIX);
        if (!scenarioDomain) return null;

        if (path.endsWith("/completeness")) return "SCENARIO_COMPLETENESS_READ";
        if (path.endsWith("/graph")) return "SCENARIO_GRAPH_READ";
        if (path.endsWith("/runs")) return "SCENARIO_RUN_READ";
        if (path.indexOf("/cognition/") >= 0) return "SCENARIO_COGNITION_READ";
        // 决策/动作读侧（F07-10/11；端点属分册 09 未建，命中即审计，前向占位）
        if (path.indexOf("/decisions") >= 0 || path.indexOf("/actions") >= 0) return "SCENARIO_DECISION_READ";
        if (path.endsWith("/minds") || path.endsWith("/bindings") || path.endsWith("/binding-links")) {
            return "SCENARIO_BIND_READ";
        }
        // 场景列表（恰好等于基址）与详情（基址 + 恰好单段 id）
        String[] segs = path.split("/");
        int baseEnd = path.startsWith(SCENARIO_WS_PREFIX)
                ? SCENARIO_WS_PREFIX.split("/").length
                : SCENARIO_BIZ_PREFIX.split("/").length;
        int idSegs = segs.length - baseEnd;
        if (idSegs == 0 || idSegs == 1) return "SCENARIO_READ";
        return null;
    }

    /** 场景 id 提取：场景域 {@code /scenarios/<id>} 段的 id；非场景域不取。 */
    static String scenarioIdFromUri(String uri) {
        if (uri == null) return null;
        String path = uri;
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        int i = path.indexOf("/scenarios/");
        if (i < 0) return null;
        String rest = path.substring(i + "/scenarios/".length());
        if (rest.isEmpty()) return null;
        String first = rest.split("/", 2)[0];
        return first.isEmpty() ? null : first;
    }

    /** 查询参取值（如认知四端点 {@code ?mind=}）；无则 null。 */
    static String queryParam(String uri, String key) {
        if (uri == null) return null;
        String query = uri;
        int q = query.indexOf('?');
        if (q < 0 || q == query.length() - 1) return null;
        query = query.substring(q + 1);
        for (String kv : query.split("&")) {
            int eq = kv.indexOf('=');
            if (eq > 0 && key.equals(kv.substring(0, eq))) {
                String v = kv.substring(eq + 1);
                try {
                    v = java.net.URLDecoder.decode(v, "UTF-8");
                } catch (Exception ignored) { /* 保持原样 */ }
                return v.isEmpty() ? null : v;
            }
        }
        return null;
    }

    /**
     * 构建审计事件载荷。E-5 红线：读侧只带 ID + 分类 + subject + traceId + 状态，绝不携带
     * 结果原文 / 行数 / coverage 数值。写侧 {@code readUri == null}。
     */
    Map<String, Object> buildEvent(String category, String className, String methodName,
                                   String httpMethod, String uri, String userId,
                                   int statusCode, boolean ok, String readUri) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("timestamp", Instant.now().toString());
        event.put("module", "workspace");
        event.put("auditKind", category);
        event.put("subjectId", userId);
        String traceId = TraceContext.current();
        if (traceId != null && !traceId.isBlank()) {
            event.put("traceId", traceId);
        }
        event.put("action", httpMethod + " " + uriNoQuery(uri));
        event.put("resource", className + "#" + methodName);
        event.put("ok", ok);
        event.put("statusCode", statusCode);
        if (readUri != null) {
            String sid = scenarioIdFromUri(uri);
            if (sid != null) event.put("scenarioId", sid);
            String mind = queryParam(uri, "mind");
            if (mind != null) event.put("mindId", mind);
        }
        return event;
    }

    private static String uriNoQuery(String uri) {
        if (uri == null) return null;
        int q = uri.indexOf('?');
        return q < 0 ? uri : uri.substring(0, q);
    }

    /** 审计发布点（供测试捕获）：EventBus 类名反射优先 → kafkaTemplate 兜底 → WARN 留痕。 */
    void emitAudit(Map<String, Object> event) {
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
            log.debug("WorkspaceSecurityEngineAuditInterceptor: EventBusService 发布失败，转兜底: {}", e.getMessage());
        }
        if (kafkaTemplateBean != null) {
            try {
                Method send = kafkaTemplateBean.getClass().getMethod("send", String.class, Object.class);
                send.invoke(kafkaTemplateBean, KafkaTopics.AUDIT, event);
                return;
            } catch (Exception e) {
                log.debug("WorkspaceSecurityEngineAuditInterceptor: kafkaTemplate 兜底失败: {}", e.getMessage());
            }
        }
        log.warn("WorkspaceSecurityEngineAuditInterceptor: EventBus/Kafka 均不可用，审计仅记日志: action={} subjectId={}",
                event.get("action"), event.get("subjectId"));
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
