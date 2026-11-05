package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.gateway.filter.EcosTrustHeaderFilter;
import com.chinacreator.gzcm.gateway.filter.QuotaFilter;
import com.chinacreator.gzcm.gateway.filter.RateLimitFilter;
import com.chinacreator.gzcm.gateway.filter.RequestContextFilter;
import com.chinacreator.gzcm.gateway.filter.SecurityHeadersFilter;
import com.chinacreator.gzcm.gateway.filter.VersionPrefixRewriteFilter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.slf4j.MDC;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 详细设计-00 B.4.1 平台入口诊断页面（F00-15/B.2）— 唯一入口 {@code GET /api/v1/system/gateway-diagnostics}。
 *
 * <p>页面对齐 B.2 布局线框（7 制品×承流态 / 过滤链顺序 / route-manifest 校验 / 匿名清单 / 档位矩阵）。
 * 携带 traceId（W08/W09），权限要求 3 级及以上（复用 {@link PreAuthorize}）。</p>
 *
 * <p><b>routeManifest 的实态（本批实测订正）</b>：旧实现
 * {@code resp.put("routeManifest", routeManifest(result -> resp.put(...summary...)))} 的两步 put
 * 中，外层 put 用方法返回值<b>覆盖</b>了回调写入的摘要——成功腿响应里是<b>原始 manifest 文档</b>，
 * 资源缺失/读失败时是 {@code null}；摘要形态（{@code present/drive/error/count/...}）从未真正出网。
 * 本批按实态保真：字段类型改 {@link JsonNode}（同一 JSON 树、同一键序），并删除那段只算不出的
 * 死摘要计算。</p>
 */
@RestController
@RequestMapping("/api/v1/system")
public class GatewayDiagnosticsController {

    private final ObjectMapper mapper = new ObjectMapper();

    /** 7 制品×承流态条目（name/port/mode/active）。 */
    public record ArtifactVO(@JsonProperty("name") String name,
                             @JsonProperty("port") int port,
                             @JsonProperty("mode") String mode,
                             @JsonProperty("active") boolean active) {
    }

    /** 过滤链条目（{@code ORDER} 为既有大写键，逐字保旧）。 */
    public record FilterLinkVO(@JsonProperty("index") int index,
                               @JsonProperty("name") String name,
                               @JsonProperty("ORDER") Integer order,
                               @JsonProperty("specIndex") int specIndex) {
    }

    /** 档位能力矩阵（三档固定）。 */
    public record EditionCapabilitiesVO(@JsonProperty("standard") List<String> standard,
                                        @JsonProperty("enterprise") List<String> enterprise,
                                        @JsonProperty("ultimate") List<String> ultimate) {
    }

    /** 档位矩阵（activeProfile + capabilities）。 */
    public record EditionMatrixVO(@JsonProperty("activeProfile") String activeProfile,
                                  @JsonProperty("capabilities") EditionCapabilitiesVO capabilities) {
    }

    /** 诊断页总响应（6 键；traceId 允许 null，键位恒在；routeManifest 为原始 manifest 或 null）。 */
    public record DiagnosticsVO(@JsonProperty("artifacts") List<ArtifactVO> artifacts,
                                @JsonProperty("filterChain") List<FilterLinkVO> filterChain,
                                @JsonProperty("routeManifest") JsonNode routeManifest,
                                @JsonProperty("anonymousEndpoints") Map<String, Map<String, String>> anonymousEndpoints,
                                @JsonProperty("editionMatrix") EditionMatrixVO editionMatrix,
                                @JsonProperty("traceId") String traceId) {
    }

    @GetMapping("/gateway-diagnostics")
    @PreAuthorize("hasAuthority('platform:diagnostics:read') or hasRole('ADMIN')")
    public ApiResponse<DiagnosticsVO> gatewayDiagnostics() {
        DiagnosticsVO resp = new DiagnosticsVO(artifacts(), filterChain(), routeManifest(),
                com.chinacreator.gzcm.common.security.anonymous.AnonymousEndpointRegistry.diagnosticEntries(),
                editionMatrix(), MDC.get("traceId"));
        return ApiResponse.success(resp).withTraceIdFromMdc();
    }

    /** 7 制品 × 承流态（C.6.1 ADR-15 口径的最小表达；运行时只判定 jar 存活与端口）。 */
    private List<ArtifactVO> artifacts() {
        List<ArtifactVO> out = new ArrayList<>();
        out.add(new ArtifactVO("gateway", 8080, "monolith", true));
        out.add(new ArtifactVO("sysman", 18081, "service", false));
        out.add(new ArtifactVO("datanet", 18082, "service", true));
        out.add(new ArtifactVO("buszhi", 18083, "service", true));
        out.add(new ArtifactVO("aiming", 18084, "service", true));
        out.add(new ArtifactVO("dccheng", 18086, "service", true));
        out.add(new ArtifactVO("workspace", 18090, "service", true));
        return out;
    }

    /** C.1 过滤器链 + 规定顺序对照表：诊断页展示"当前序 vs 规定序"（W11 门禁）。 */
    private List<FilterLinkVO> filterChain() {
        List<Class<? extends Filter>> chain = new ArrayList<>();
        chain.add(VersionPrefixRewriteFilter.class);
        chain.add(RequestContextFilter.class);
        chain.add(EcosTrustHeaderFilter.class);
        chain.add(SecurityHeadersFilter.class);
        chain.add(RateLimitFilter.class);
        chain.add(QuotaFilter.class);
        List<FilterLinkVO> out = new ArrayList<>();
        for (int i = 0; i < chain.size(); i++) {
            Class<? extends Filter> c = chain.get(i);
            out.add(new FilterLinkVO(i, c.getSimpleName(), orderOf(c), i)); // 规定序 = 现态（W11 落位后一致）
        }
        return out;
    }

    private Integer orderOf(Class<?> c) {
        Order order = AnnotationUtils.findAnnotation(c, Order.class);
        if (order == null) return null;
        return order.value();
    }

    /** 原始 route-manifest 文档（资源缺失/读失败 ⇒ null，与旧出网形态一致）。 */
    private JsonNode routeManifest() {
        try (InputStream in = GatewayDiagnosticsController.class.getResourceAsStream("/route-manifest.json")) {
            if (in == null) {
                return null;
            }
            return mapper.readTree(in.readAllBytes());
        } catch (IOException e) {
            return null;
        }
    }

    private EditionMatrixVO editionMatrix() {
        EditionCapabilitiesVO capabilities = new EditionCapabilitiesVO(
                List.of("PG", "pgvector", "MinIO-Raw", "Kafka-DLQ", "OPA"),
                List.of("PG", "Neo4j", "pgvector", "MinIO-Raw", "Kafka-DLQ", "OPA"),
                List.of("PG", "Neo4j", "pgvector", "DorisClickHouse", "MinIO-Raw", "Kafka-DLQ", "OPA"));
        return new EditionMatrixVO(System.getProperty("spring.profiles.active", "standard"), capabilities);
    }
}
