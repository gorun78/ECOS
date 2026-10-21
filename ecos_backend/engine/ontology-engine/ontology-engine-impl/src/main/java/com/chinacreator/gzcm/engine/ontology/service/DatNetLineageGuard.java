package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.dto.GateResult;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * F03-03 V3 — 血缘列存在守卫（经 data-engine 元数据 REST 校验事实表列真实存在）。
 *
 * <p><b>守红线</b>：禁跨 schema 直查 {@code ecos_dw}（铁律 §2.1 / 数据访问规范 ST07）——
 * 列的<b>真实存在性</b>一律经 data-engine 元数据 REST
 * {@code GET {base}/api/v1/datanet/metadata/columns?tableRef=...} 校验，
 * base 经 {@link ServiceEndpointResolver} 两态寻址（route-manifest 单源）。响应契约
 * {@code {"code":0,"data":{"columns":["c1","c2",...]}}}。</p>
 *
 * <p><b>列供给解耦（可测试性）</b>：核心判定只依赖 {@link LineageColumnFetcher}——
 * 给 tableRef 返回其列集（无法解析/不可达时返回 {@code null} 表示依赖不可用）。
 * 生产装配 {@link RestLineageColumnFetcher}（经 RestTemplate 真实调用，3s 超时）；
 * 单元测试直接 stub 该 fetcher，无需真实 HTTP。</p>
 *
 * <p><b>三态</b>：</p>
 * <ul>
 *   <li>依赖不可用（resolver 空 / HTTP 404/5xx / 连接失败 / 响应非法）→
 *       {@code dependencyErrors += DependencyError("datanet","ECOS-ONTO-050")}（503 fail-closed，禁默认通过）；</li>
 *   <li>HTTP 200 但某声明列缺失 → {@code GateViolation(V3_LINEAGE, ECOS-ONTO-030, "lineage column '{col}' missing on table '{table}'")}。</li>
 * </ul>
 */
public final class DatNetLineageGuard {

    private static final Logger log = LoggerFactory.getLogger(DatNetLineageGuard.class);

    /** 无血缘引用 → PASS 的哨兵：非空才校验。 */
    private static final String DEP_DATANET = "datanet";

    private final LineageColumnFetcher fetcher;

    public DatNetLineageGuard(LineageColumnFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /**
     * V3 判定主入口。
     *
     * @param tableRef     事实表引用（schema.table 或裸表名）
     * @param declaredCols 提案/指标声明的列（可能为空 → 无列可校验则 PASS）
     * @param result       门禁结果累加器
     */
    void check(String tableRef, List<String> declaredCols, GateResult result) {
        if (tableRef == null || tableRef.isBlank()) {
            return;
        }
        List<String> actual;
        try {
            actual = fetcher.columns(tableRef);
        } catch (ResourceAccessException | HttpClientErrorException | HttpServerErrorException e) {
            log.error("V3 lineage: datanet metadata REST unavailable tableRef={} — fail-closed ECOS-ONTO-050",
                    tableRef, e);
            result.getDependencyErrors().add(
                    new GateResult.DependencyError(DEP_DATANET, GateResult.CODE_DEPENDENCY_UNAVAILABLE));
            return;
        } catch (RuntimeException e) {
            log.error("V3 lineage: unexpected fetcher failure tableRef={} — fail-closed ECOS-ONTO-050", tableRef, e);
            result.getDependencyErrors().add(
                    new GateResult.DependencyError(DEP_DATANET, GateResult.CODE_DEPENDENCY_UNAVAILABLE));
            return;
        }

        // fetcher 返回 null = 依赖不可用（resolver 空 / 404 / 5xx / 响应非法）
        if (actual == null) {
            log.warn("V3 lineage: datanet metadata column set unresolvable tableRef={} — fail-closed ECOS-ONTO-050", tableRef);
            result.getDependencyErrors().add(
                    new GateResult.DependencyError(DEP_DATANET, GateResult.CODE_DEPENDENCY_UNAVAILABLE));
            return;
        }

        if (declaredCols == null || declaredCols.isEmpty()) {
            return; // 事实表可达、无声明列 → PASS
        }
        java.util.List<String> actualLower =
                actual.stream().filter(java.util.Objects::nonNull).map(String::trim).toList();
        for (String col : declaredCols) {
            if (col == null || col.isBlank()) continue;
            boolean found = actualLower.contains(col.trim());
            if (!found) {
                result.getViolations().add(new GateResult.GateViolation(
                        GateResult.GATE_V3_LINEAGE, GateResult.CODE_LINEAGE_COLUMN_MISSING,
                        "lineage column '" + col + "' missing on table '" + tableRef + "'")
                        .addRef(tableRef).addRef(col));
            }
        }
    }

    /**
     * 列集供给抽象（tableRef → 列名列表）。
     * <p>生产 = {@link RestLineageColumnFetcher}（真实 REST）；测试 = lambda stub。</p>
     *
     * @return 列名列表；<b>null = 依赖不可用</b>（触发 503 ECOS-ONTO-050）；空列表 = 表存在但无列（合法）。
     */
    @FunctionalInterface
    public interface LineageColumnFetcher {
        List<String> columns(String tableRef);
    }

    /**
     * 生产装配：经 {@link ServiceEndpointResolver} 两态寻址 + 限定 {@link RestTemplate}（3s）
     * 调 data-engine 元数据端点 {@code GET {base}/api/v1/datanet/metadata/columns?tableRef=...}。
     *
     * <p>不可用（resolver 空 / 404 / 5xx / 连接失败 / 响应非法）→ 返回 <b>null</b>，
     * 由 {@link DatNetLineageGuard#check} 统一折算成 503 ECOS-ONTO-050（fail-closed）。</p>
     *
     * <p><b>P0-3 第二处断点修复（2026-10-14）</b>：datanet 面不在匿名单源
     * {@code AnonymousEndpointRegistry} 内（未登记 = {@code anyRequest().authenticated()} 默认 DENY），
     * 故裸 {@code RestTemplate} 调用必然 401 —— 即便服务端端点存在，V3 仍恒 fail-closed。
     * 修法取<b>「透传入站 JWT」</b>而非「把端点登记成匿名」：
     * 前者保持调用者身份与 L1 准入语义（详设 §3073 该端点定级 L1），
     * 后者会把列清单变成无凭证可枚举的信息泄露面。
     * 无请求上下文（异步/定时触发）时不透传 → 401 → 依旧 fail-closed，不放开。</p>
     */
    static final class RestLineageColumnFetcher implements LineageColumnFetcher {

        private final ServiceEndpointResolver endpointResolver;
        private final RestTemplate restTemplate;

        /** 入站凭证供给（生产 = 当前 HTTP 请求的 Authorization 头；测试可注入桩）。 */
        private final Supplier<String> authorizationSupplier;

        public RestLineageColumnFetcher(ServiceEndpointResolver endpointResolver, RestTemplate restTemplate) {
            this(endpointResolver, restTemplate, RestLineageColumnFetcher::currentInboundAuthorization);
        }

        /** 包可见装配位：单测注入凭证供给，无需真实 servlet 上下文。 */
        RestLineageColumnFetcher(ServiceEndpointResolver endpointResolver, RestTemplate restTemplate,
                                 Supplier<String> authorizationSupplier) {
            this.endpointResolver = endpointResolver;
            this.restTemplate = restTemplate;
            this.authorizationSupplier = authorizationSupplier != null
                    ? authorizationSupplier : () -> null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<String> columns(String tableRef) {
            if (endpointResolver == null) {
                return null;
            }
            String base = endpointResolver.resolve("datanet", ServiceEndpointResolver.RouteMode.MONOLITH).orElse(null);
            if (base == null || base.isBlank()) {
                return null; // resolver 未登记 datanet → 依赖不可用
            }
            String url = base + "/api/v1/datanet/metadata/columns?tableRef=" + java.net.URLEncoder.encode(tableRef, java.nio.charset.StandardCharsets.UTF_8);
            try {
                ResponseEntity<Map> resp = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(requestHeaders()), Map.class);
                Map body = resp.getBody();
                if (body == null) return null;
                Object data = body.get("data");
                Object columns = data instanceof java.util.Map ? ((java.util.Map<String, Object>) data).get("columns") : null;
                if (columns instanceof List) {
                    List<String> names = new java.util.ArrayList<>();
                    for (Object c : (List<Object>) columns) {
                        if (c != null) names.add(String.valueOf(c));
                    }
                    return names;
                }
                return null; // 响应非法 → 依赖不可用
            } catch (HttpClientErrorException e) {
                log.warn("V3 lineage: datanet metadata HTTP {} tableRef={}", e.getStatusCode(), tableRef);
                return null; // 4xx（含 404/401）→ 依赖不可用
            } catch (HttpServerErrorException e) {
                log.warn("V3 lineage: datanet metadata 5xx tableRef={} status={}", tableRef, e.getStatusCode());
                return null; // 5xx → 依赖不可用
            } catch (ResourceAccessException e) {
                log.warn("V3 lineage: datanet metadata unreachable tableRef={} — {}", tableRef, e.getMessage());
                return null; // 连接失败 → 依赖不可用
            } catch (RuntimeException e) {
                log.warn("V3 lineage: datanet metadata fetch error tableRef={}", tableRef, e);
                return null;
            }
        }

        /** 组装出向请求头：仅在拿到入站凭证时带 Authorization（缺凭证不加头，让服务端按默认 DENY 处置）。 */
        private HttpHeaders requestHeaders() {
            HttpHeaders headers = new HttpHeaders();
            String authorization = authorizationSupplier.get();
            if (authorization != null && !authorization.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, authorization.trim());
            }
            return headers;
        }

        /**
         * 从当前请求上下文取入站 Authorization（判据单源在 {@link CallerCredentialSource}，
         * #142/B4-3e-21 收口 D-B4-61）；非 HTTP 线程（异步/定时）返回 {@code null}。
         */
        static String currentInboundAuthorization() {
            return CallerCredentialSource.currentAuthorization();
        }
    }
}
