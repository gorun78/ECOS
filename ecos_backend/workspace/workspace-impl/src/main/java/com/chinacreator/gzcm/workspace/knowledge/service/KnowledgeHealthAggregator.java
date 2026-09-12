package com.chinacreator.gzcm.workspace.knowledge.service;

import com.chinacreator.gzcm.workspace.knowledge.dto.KnowledgeHealthVO;
import com.chinacreator.gzcm.workspace.knowledge.dto.KnowledgeHealthVO.EngineHealthVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 知识工作台健康检查聚合器 — E-A#1 产物。
 *
 * <p>探测策略（铁律 §1.2 "不 import 其他 service 的 bean"，全部走 REST）：
 * <ul>
 *   <li>workspace 与 gateway 同 JVM 部署时，对各引擎的 {@code /api/v1/engine/{type}/health}
 *       / {@code /api/v1/cognitive/health} 做 REST 直探（1.5s 超时）；</li>
 *   <li>单体 fat-JAR 场景下这部分全部命中本 gateway :8080 内同路由 Controller（等价）；</li>
 *   <li>切微服务后 gateway 上加 7-companion 路由（P2-6），自然指向 5 个 service 端口。</li>
 * </ul>
 *
 * <p>降级规则：单引擎 REST 失败 → status=DOWN + components.failed=true + reason
 * （HTTP 仍 200，不单点拖累前端）。
 *
 * @author ecos-factory
 * @since PMO-55 批次 E-A
 */
@Service("ecosKnowledgeHealthAggregator")
public class KnowledgeHealthAggregator {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeHealthAggregator.class);

    /**
     * 6 引擎 health 探活端点模板（相对 gatewayBase）。
     * <ul>
     *   <li>kb — 本地 DI bean (ObjectProvider) 优先，fallback REST</li>
     *   <li>ai — REST（gateway 内 AiEngineAliasController 同 classpath 暴露 /api/v1/engine/ai/health）</li>
     *   <li>security — REST（同上 /api/v1/engine/security/health）</li>
     *   <li>data — REST（同上 /api/v1/engine/data/health）</li>
     *   <li>ontology — REST（同上 /api/v1/engine/ontology/health，Neo4j 健康内嵌 components）</li>
     *   <li>cognitive — REST（dccheng 服务 :18086 上 cognitive2 端点；单体内无该 controller → 404 → DOWN）</li>
     * </ul>
     * 全部走 gatewayBase（默认 http://localhost:8080），单体时命中本 JVM 路由；
     * 切微服务后 gateway 上加 7-companion 路由指向 5 个 service 端口。
     */
    private static final Map<String, String> ENGINE_PATHS = new LinkedHashMap<>();

    static {
        ENGINE_PATHS.put("kb", "/api/v1/engine/knowledge/health");
        ENGINE_PATHS.put("ai", "/api/v1/engine/ai/health");
        ENGINE_PATHS.put("security", "/api/v1/engine/security/health");
        ENGINE_PATHS.put("data", "/api/v1/engine/data/health");
        ENGINE_PATHS.put("ontology", "/api/v1/engine/ontology/health");
        // 认知阶段 :18086 上 cognitive2 （V116/PMO-54 之后才在该 service 上启用）
        ENGINE_PATHS.put("cognitive", "http://localhost:18086/api/v1/cognitive/health");
    }

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /** 探活 supplier — 每引擎包装成一个 Function<name, EngineHealthVO> box 说明。 */
    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbc;
    private final String gatewayBase;

    public KnowledgeHealthAggregator(
            JdbcTemplate jdbc,
            @Value("${ecos.gateway-base:http://localhost:8080}") String gatewayBase) {
        this.restTemplate = newShortTimeoutRestTemplate();
        this.jdbc = jdbc;
        this.gatewayBase = gatewayBase;
    }

    /**
     * 短超时 RestTemplate 工厂（1500ms）— 避免全局 RestTemplate 的默认无聊行为
     * 拖跨认知 DOWN 探活时长。不创建独立 bean，仅内部构造 — 不影响 bean 图。
     */
    private static RestTemplate newShortTimeoutRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(1500);
        return new RestTemplate(factory);
    }

    /**
     * 聚合 6 引擎 + pgdb + pgVector + latencyMs，一次性返回。
     *
     * <p>每引擎全走 REST（单体：gatewayBase JAR 内部路由；微服务：直探各 service 端口）。
     * 返回值：每个 engine 结构化 status / reason / components；任一 DOWN → overall=DEGRADED。
     */
    public KnowledgeHealthVO aggregate() {
        long startNanos = System.nanoTime();
        KnowledgeHealthVO vo = new KnowledgeHealthVO();
        Map<String, EngineHealthVO> engines = new LinkedHashMap<>(ENGINE_PATHS.size());
        // 保留声明顺序（kb / ai / security / data / ontology / cognitive）
        for (Map.Entry<String, String> e : ENGINE_PATHS.entrySet()) {
            engines.put(e.getKey(), probeEngine(e.getKey(), e.getValue()));
        }
        vo.setEngines(engines);
        vo.setPgdb(probePgDatabase());
        vo.setPgVector(probePgVector());
        vo.setOverall(overallOf(engines));
        vo.setLatencyMs((System.nanoTime() - startNanos) / 1_000_000L);
        return vo;
    }

    // ── 探活辅助 ──────────────────────────────────────

    private EngineHealthVO probeEngine(String name, String path) {
        EngineHealthVO eh = new EngineHealthVO();
        String url = gatewayBase + path;
        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) {
                eh.setStatus("DOWN");
                eh.setReason("remote-empty");
                markFailed(eh);
                return eh;
            }
            // ApiResponse 包裹解 data；cognitive 直返 Map（status 在顶层）
            Object statusWrapper = body.get("data");
            if (!(statusWrapper instanceof Map<?, ?> dataMap)) {
                // cognitive 路径 — status 在顶层
                Object status = body.get("status");
                boolean up = status != null && "UP".equalsIgnoreCase(String.valueOf(status));
                eh.setStatus(up ? "UP" : "DOWN");
                eh.setReason("remote");
                for (Map.Entry<String, Object> kv : body.entrySet()) {
                    if (kv.getKey() == null || "status".equals(kv.getKey())) {
                        continue;
                    }
                    eh.getComponents().put(kv.getKey(), kv.getValue());
                }
                if (!up) {
                    markFailed(eh);
                }
                return eh;
            }
            // 标准 HealthCheck：{status, components}
            Object status = dataMap.get("status");
            Object components = dataMap.get("components");
            boolean up = status != null && "UP".equalsIgnoreCase(String.valueOf(status));
            eh.setStatus(up ? "UP" : "DOWN");
            eh.setReason("remote");
            if (components instanceof Map<?, ?> comps) {
                for (Map.Entry<?, ?> c : comps.entrySet()) {
                    if (c.getKey() != null) {
                        eh.getComponents().put(String.valueOf(c.getKey()), c.getValue());
                    }
                }
            }
            if (!up) {
                markFailed(eh);
            }
            return eh;
        } catch (Exception ex) {
            log.debug("{} engine health probe failed ({}): {}", name, url, shortMsg(ex));
            eh.setStatus("DOWN");
            eh.setReason("remote-" + shortMsg(ex));
            markFailed(eh);
            return eh;
        }
    }

    private String probePgDatabase() {
        try {
            Long one = jdbc.queryForObject("SELECT 1", Long.class);
            return one != null ? "UP" : "DOWN";
        } catch (Exception ex) {
            log.error("pgdb probe failed: {}", shortMsg(ex));
            return "DOWN";
        }
    }

    private String probePgVector() {
        try {
            Boolean has = jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'vector')",
                    Boolean.class);
            return Boolean.TRUE.equals(has) ? "UP" : "NOT_INSTALLED";
        } catch (Exception ex) {
            log.error("pgvector probe failed: {}", shortMsg(ex));
            return "DOWN";
        }
    }

    /**
     * 整体状态：任一 DOWN → DEGRADED；全 UP → UP。
     */
    private String overallOf(Map<String, EngineHealthVO> engines) {
        for (EngineHealthVO eh : engines.values()) {
            if ("DOWN".equals(eh.getStatus())) {
                return "DEGRADED";
            }
        }
        return "UP";
    }

    private void markFailed(EngineHealthVO eh) {
        Map<String, Object> comps = eh.getComponents();
        if (!comps.containsKey("failed")) {
            comps.put("failed", true);
            comps.put("probedAt", OffsetDateTime.now().format(TS_FMT));
        }
    }

    private static String shortMsg(Throwable t) {
        String msg = t.getClass().getSimpleName();
        if (t.getMessage() != null) {
            msg = msg + ": " + t.getMessage();
        }
        return msg.length() > 120 ? msg.substring(0, 120) : msg;
    }
}
