package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.gateway.filter.EcosTrustHeaderFilter;
import com.chinacreator.gzcm.gateway.filter.QuotaFilter;
import com.chinacreator.gzcm.gateway.filter.RateLimitFilter;
import com.chinacreator.gzcm.gateway.filter.RequestContextFilter;
import com.chinacreator.gzcm.gateway.filter.SecurityHeadersFilter;
import com.chinacreator.gzcm.gateway.filter.VersionPrefixRewriteFilter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 详细设计-00 B.4.1 平台入口诊断页面（F00-15/B.2）— 唯一入口 {@code GET /api/v1/system/gateway-diagnostics}。
 *
 * <p>页面对齐 B.2 布局线框（7 制品×承流态 / 过滤链顺序 / route-manifest 校验 / 匿名清单 / 档位矩阵）。
 * 携带 traceId（W08/W09），权限要求 3 级及以上（复用 {@link PreAuthorize}）。
 */
@RestController
@RequestMapping("/api/v1/system")
public class GatewayDiagnosticsController {

    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping("/gateway-diagnostics")
    @PreAuthorize("hasAuthority('platform:diagnostics:read') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> gatewayDiagnostics() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("artifacts", artifacts());
        resp.put("filterChain", filterChain());
        resp.put("routeManifest", routeManifest(result -> resp.put("routeManifest", summary(result))));
        resp.put("anonymousEndpoints", com.chinacreator.gzcm.common.security.anonymous.AnonymousEndpointRegistry.diagnosticEntries());
        resp.put("editionMatrix", editionMatrix());
        resp.put("traceId", MDC.get("traceId"));
        return ApiResponse.success(resp).withTraceIdFromMdc();
    }

    /** 7 制品 × 承流态（C.6.1 ADR-15 口径的最小表达；运行时只判定 jar 存活与端口）。 */
    private List<Map<String, Object>> artifacts() {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(entry("gateway", 8080, "monolith", true));
        out.add(entry("sysman", 18081, "service", false));
        out.add(entry("datanet", 18082, "service", true));
        out.add(entry("buszhi", 18083, "service", true));
        out.add(entry("aiming", 18084, "service", true));
        out.add(entry("dccheng", 18086, "service", true));
        out.add(entry("workspace", 18090, "service", true));
        return out;
    }

    private Map<String, Object> entry(String name, int port, String mode, boolean active) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("port", port);
        m.put("mode", mode);
        m.put("active", active);
        return m;
    }

    /** C.1 过滤器链 + 规定顺序对照表：诊断页展示"当前序 vs 规定序"（W11 门禁）。 */
    private List<Map<String, Object>> filterChain() {
        List<Class<? extends Filter>> chain = new ArrayList<>();
        chain.add(VersionPrefixRewriteFilter.class);
        chain.add(RequestContextFilter.class);
        chain.add(EcosTrustHeaderFilter.class);
        chain.add(SecurityHeadersFilter.class);
        chain.add(RateLimitFilter.class);
        chain.add(QuotaFilter.class);
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < chain.size(); i++) {
            Class<? extends Filter> c = chain.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", i);
            m.put("name", c.getSimpleName());
            m.put("ORDER", orderOf(c));
            m.put("specIndex", i); // 规定序 = 现态（W11 落位后一致）
            out.add(m);
        }
        return out;
    }

    private Integer orderOf(Class<?> c) {
        Order order = AnnotationUtils.findAnnotation(c, Order.class);
        if (order == null) return null;
        return order.value();
    }

    @SuppressWarnings("unchecked")
    private Object routeManifest(java.util.function.Consumer<Object> sink) {
        try (InputStream in = GatewayDiagnosticsController.class.getResourceAsStream("/route-manifest.json")) {
            if (in == null) {
                Map<String, Object> missing = new LinkedHashMap<>();
                missing.put("present", false);
                missing.put("drive", "docs/10-架构/refs/route-manifest.json");
                sink.accept(missing);
                return null;
            }
            byte[] bytes = in.readAllBytes();
            Map<String, Object> manifest = mapper.readValue(bytes, new TypeReference<Map<String, Object>>() {});
            sink.accept(summary(manifest));
            return manifest;
        } catch (IOException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("present", false);
            err.put("error", e.getMessage());
            sink.accept(err);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> summary(Object manifest) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!(manifest instanceof Map)) {
            out.put("count", 0);
            out.put("serviceRoutes", 0);
            out.put("monolithRoutes", 0);
            return out;
        }
        List<Object> entries = (List<Object>) (((Map<String, Object>) manifest).get("entries"));
        int svc = 0, mono = 0;
        for (Object o : entries) {
            Map<String, Object> e = (Map<String, Object>) o;
            if ("service".equals(e.get("route"))) svc++; else mono++;
        }
        out.put("count", entries.size());
        out.put("serviceRoutes", svc);
        out.put("monolithRoutes", mono);
        return out;
    }

    private Map<String, Object> editionMatrix() {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("standard", List.of("PG", "pgvector", "MinIO-Raw", "Kafka-DLQ", "OPA"));
        capabilities.put("enterprise", List.of("PG", "Neo4j", "pgvector", "MinIO-Raw", "Kafka-DLQ", "OPA"));
        capabilities.put("ultimate", List.of("PG", "Neo4j", "pgvector", "DorisClickHouse", "MinIO-Raw", "Kafka-DLQ", "OPA"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("activeProfile", System.getProperty("spring.profiles.active", "standard"));
        out.put("capabilities", capabilities);
        return out;
    }
}
