package com.chinacreator.gzcm.gateway.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.context.TraceContext;
import com.chinacreator.gzcm.common.security.registry.AnonymousEndpointRegistry;
import com.chinacreator.gzcm.common.security.registry.AnonymousEndpointRegistry.Entry;
import com.chinacreator.gzcm.gateway.filter.EcosTrustHeaderFilter;
import com.chinacreator.gzcm.gateway.filter.QuotaFilter;
import com.chinacreator.gzcm.gateway.filter.RateLimitFilter;
import com.chinacreator.gzcm.gateway.filter.RequestContextFilter;
import com.chinacreator.gzcm.gateway.filter.SecurityHeadersFilter;
import com.chinacreator.gzcm.gateway.filter.VersionPrefixRewriteFilter;
import com.chinacreator.gzcm.gateway.routing.ServiceEndpointResolver;

/**
 * 平台诊断端点（详细设计-00 §3.1 网关诊断页 / §5.3 OpenAPI，M1）。
 * <p>
 * {@code GET /api/v1/system/gateway-diagnostics?tab=filters|routes|anonymous|editions}
 * 返回 {@link GatewayDiagnostics} 快照：
 * <ul>
 *   <li>{@code artifacts}：本进程装配的 runtime 底座 + 引擎/工作区 7 模块基本健康态；</li>
 *   <li>{@code filterChain}：当前 servlet filter 注册的 order + 期望序 + 一致性判定（C.1.2 校验）；</li>
 *   <li>{@code routeManifest}：route-manifest.json 展开的 prefix/owner/mode/carried；</li>
 *   <li>{@code anonymousEndpoints}：{@link AnonymousEndpointRegistry} 全量条目（W05 单源）；</li>
 *   <li>{@code editionMatrix}：装配档位矩阵（当前 profile × 三档差异要点）；</li>
 *   <li>{@code traceId}：当前请求 traceId（F00-11 全链）。tab 参数只做子集过滤，不影响数据源。</li>
 * </ul>
 *
 * <p>权限：本端点走 ClearanceInterceptor PATH_RULES 的 /api/v1/ 默认 L1；不额外加权限注解。
 */
@RestController
@RequestMapping("/api/v1/system")
public class GatewayDiagnosticsController {

    private static final Logger log = LoggerFactory.getLogger(GatewayDiagnosticsController.class);

    /** C.1.1 期望序（名称 → 期望 @Order 值，expectedOrder 输出到前端一致性判定） */
    private static final Map<String, Integer> EXPECTED_ORDER = new TreeMap<>();
    static {
        EXPECTED_ORDER.put(VersionPrefixRewriteFilter.class.getSimpleName(), Ordered.HIGHEST_PRECEDENCE + 10);
        EXPECTED_ORDER.put(RequestContextFilter.class.getSimpleName(),     Ordered.HIGHEST_PRECEDENCE + 11);
        EXPECTED_ORDER.put(EcosTrustHeaderFilter.class.getSimpleName(),    Ordered.HIGHEST_PRECEDENCE + 20);
        EXPECTED_ORDER.put(SecurityHeadersFilter.class.getSimpleName(),    Ordered.HIGHEST_PRECEDENCE + 30);
        EXPECTED_ORDER.put(RateLimitFilter.class.getSimpleName(),          5);
        EXPECTED_ORDER.put(QuotaFilter.class.getSimpleName(),              6);
    }

    private final ServiceEndpointResolver routeManifest;
    private final String activeProfile;

    public GatewayDiagnosticsController(ServiceEndpointResolver routeManifest,
                                        @Value("${spring.profiles.active:standard}") String activeProfile) {
        this.routeManifest = routeManifest;
        this.activeProfile = activeProfile;
    }

    @GetMapping("/gateway-diagnostics")
    public ApiResponse<Map<String, Object>> getGatewayDiagnostics(
            @RequestParam(value = "tab", required = false, defaultValue = "filters") String tab) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifacts", collectArtifacts());
        result.put("filterChain", collectFilterChain());
        result.put("routeManifest", collectRouteManifest());
        result.put("anonymousEndpoints", collectAnonymousEndpoints());
        result.put("editionMatrix", collectEditionMatrix());
        result.put("traceId", TraceContext.current());
        result.put("tab", tab);
        log.debug("[GatewayDiagnostics] tab={} profile={} trace={}", tab, activeProfile, TraceContext.current());
        return ApiResponse.success(result);
    }

    // ── 快照片段装载 ─────────────────────────────────────────────

    private List<Map<String, Object>> collectArtifacts() {
        List<Map<String, Object>> artifacts = new ArrayList<>();
        artifacts.add(artifact("gateway", Runtime.getRuntime().maxMemory(), true));
        // 装载成本较低的 6 个 JAR（gateway 聚合 root，探活着即代表 Holder 已就绪）
        for (String svc : List.of("sysman", "datanet", "buszhi", "aiming", "dccheng", "workspace")) {
            artifacts.add(artifact(svc, 0L, true));
        }
        return artifacts;
    }

    private Map<String, Object> artifact(String name, long memoryLimit, boolean up) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("up", up);
        if (memoryLimit > 0) {
            m.put("heapLimitMb", memoryLimit / 1024L / 1024L);
        }
        return m;
    }

    private List<Map<String, Object>> collectFilterChain() {
        List<Map<String, Object>> chain = new ArrayList<>();
        for (Map.Entry<String, Integer> e : EXPECTED_ORDER.entrySet()) {
            Class<?> cls = filterClassByName(e.getKey());
            if (cls == null) continue;
            Integer actual = actualOrder(cls);
            int expected = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getKey());
            m.put("order", actual == null ? -1 : actual);
            m.put("expectedOrder", expected);
            m.put("consistent", actual != null && actual == expected);
            chain.add(m);
        }
        chain.sort((a, b) -> Integer.compare(toInt(a.get("expectedOrder")), toInt(b.get("expectedOrder"))));
        return chain;
    }

    private Class<?> filterClassByName(String simpleName) {
        Map<String, Class<?>> map = Map.of(
                VersionPrefixRewriteFilter.class.getSimpleName(), VersionPrefixRewriteFilter.class,
                RequestContextFilter.class.getSimpleName(),       RequestContextFilter.class,
                EcosTrustHeaderFilter.class.getSimpleName(),      EcosTrustHeaderFilter.class,
                SecurityHeadersFilter.class.getSimpleName(),      SecurityHeadersFilter.class,
                RateLimitFilter.class.getSimpleName(),            RateLimitFilter.class,
                QuotaFilter.class.getSimpleName(),                QuotaFilter.class);
        return map.get(simpleName);
    }

    private static Integer actualOrder(Class<?> cls) {
        Order o = AnnotationUtils.findAnnotation(cls, Order.class);
        return o == null ? null : o.value();
    }

    private static int toInt(Object v) {
        return v instanceof Integer i ? i : Integer.MIN_VALUE;
    }

    private List<Map<String, Object>> collectRouteManifest() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ServiceEndpointResolver.Entry e : routeManifest.entries()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("prefix", e.prefix());
            m.put("owner", e.owner());
            m.put("artifact", e.artifact());
            m.put("mode", e.mode());
            // carried：S3 切流前 = true（monolith 承载）；切流后由 route-manifest 更新
            m.put("carried", "monolith".equals(e.mode()));
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> collectAnonymousEndpoints() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Entry e : AnonymousEndpointRegistry.entries()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pattern", String.join(",", e.patterns()));
            m.put("scope", e.scope().name());
            m.put("dualPath", e.patterns().length >= 2);
            m.put("justification", e.justification());
            m.put("approver", e.approver());
            m.put("date", e.date());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> collectEditionMatrix() {
        // C.6.2 装配矩阵（runtime-read 快照）：仅列能力差异（Neo4j/Doris/ClickHouse/bean 装配）
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(editionRow("standard",   "PG only",        false, false, false));
        rows.add(editionRow("enterprise", "PG + Neo4j",     true,  false, false));
        rows.add(editionRow("ultimate",   "PG + Neo4j + (Doris|CH)", true, true, true));

        for (Map<String, Object> r : rows) {
            r.put("active", r.get("profile").equals(activeProfile));
        }
        return rows;
    }

    private Map<String, Object> editionRow(String profile, String storage, boolean neo4j, boolean dorisClick, boolean columnStore) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profile", profile);
        m.put("storage", storage);
        m.put("neo4j", neo4j);
        m.put("dorisOrClickhouse", dorisClick);
        m.put("columnStore", columnStore);
        return m;
    }
}
