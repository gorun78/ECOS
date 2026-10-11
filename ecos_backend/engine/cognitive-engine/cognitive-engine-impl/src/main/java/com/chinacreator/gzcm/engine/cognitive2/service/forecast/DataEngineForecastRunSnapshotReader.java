package com.chinacreator.gzcm.engine.cognitive2.service.forecast;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveContractSchema;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.ScenarioBalanceChecker.Cell;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link ForecastRunSnapshotReader} 的真实实现（H-2b / 详设-09 v1.2 §十一 H-2、H-5 认知侧半区）。
 *
 * <p><b>ST08 读通道</b>：认知域禁直连 {@code ecos_dw}，快照明细唯一来源 = data-engine 只读 REST
 * {@code GET /api/v1/datanet/forecast-runs/{runId}/snapshot}（卷 02 H-3 交付③）。基址经
 * runtime-access {@link ServiceEndpointResolver} 两态寻址（铁律 §2.5 底座收口，禁自拼
 * {@code http://localhost:180xx}），HTTP 客户端复用 {@code cognitiveRestTemplate} 共享 Bean
 * （X-35，禁 {@code new RestTemplate()}）—— 与同包 {@link ForecastArtifactWriteChannel} 写侧同款纪律。</p>
 *
 * <p><b>诚实面</b>（X-44 / DG-9）：</p>
 * <ul>
 *   <li>runId 缺位 ⇒ 400（要点 4 禁全库扫描）；</li>
 *   <li>data-engine 回业务"运行不存在" ⇒ 404 {@code ECOS-COG-404F}，<b>禁</b>伪装成空快照；</li>
 *   <li>读通道尚未上线（路由 404 / 无该端点） ⇒ 真实 <b>501</b> {@code ECOS-COG-503} + missing 定位，
 *       <b>禁 mock 成功</b>；</li>
 *   <li>FC-03 {@code scenario-copy} 确定性重算端点全仓 0 实现（H-5 属主卷 07）⇒ 直接 501，
 *       认知侧绝不本地自算情景金额（B-2 金额唯一来源铁律）。</li>
 * </ul>
 *
 * <p>六要素的 {@code snapshotId}/{@code formulaVersion}/{@code reusedFromRunId} 落在
 * {@link SnapshotLineage}（V277 增列，历史行可空 ⇒ 缺值原样 null，禁伪造回填），
 * 不挤进签名已冻结的 {@link RunSnapshot}。</p>
 */
@Component
public class DataEngineForecastRunSnapshotReader implements ForecastRunSnapshotReader {

    private static final Logger log = LoggerFactory.getLogger(DataEngineForecastRunSnapshotReader.class);

    /** 归属服务（route-manifest owner = datanet），与写通道同源。 */
    static final String DATA_OWNER_SERVICE = "datanet";
    /** FC-02 只读快照路径模板（data-engine {@code ForecastRunSnapshotController}）。 */
    static final String SNAPSHOT_PATH = "/api/v1/datanet/forecast-runs/%s/snapshot";
    /** FC-03 情景重算路径（<b>本批未上线</b>，仅作 missing 定位串，禁发起伪调用）。 */
    static final String SCENARIO_COPY_PATH = "/api/v1/datanet/forecast-runs/%s/scenario-copy";
    /** data-engine 业务 404 的原文标记（区分"运行不存在"与"端点不存在"）。 */
    static final String RUN_NOT_FOUND_MARKER = "预测运行不存在";

    private final RestTemplate restTemplate;
    private final ServiceEndpointResolver endpointResolver;

    public DataEngineForecastRunSnapshotReader(@Qualifier("cognitiveRestTemplate") RestTemplate restTemplate,
                                               ServiceEndpointResolver endpointResolver) {
        this.restTemplate = restTemplate;
        this.endpointResolver = endpointResolver;
    }

    /** 六要素增项（V277 物理列；缺值 = null，禁回填）。 */
    public record SnapshotLineage(String forecastRunId,
                                  String snapshotId,
                                  String formulaVersion,
                                  String reusedFromRunId,
                                  String scenarioId,
                                  String runType,
                                  String status,
                                  String baselineRunId) {
        /** 幂等复用命中标记（{@code reused_from_run_id} 非空即"本次运行复用既有 SUCCEEDED run"）。 */
        public boolean reused() {
            return reusedFromRunId != null && !reusedFromRunId.isBlank();
        }

        /** 复用来源运行键（{@link #reused()} 为真时非空；未复用回 null，调用方禁据此造数）。 */
        public String reusedFromRunIdOrEmpty() {
            return reused() ? reusedFromRunId : null;
        }
    }

    /** 快照 + 六要素增项（reader 的内部返回形态，供 {@link #getLineage} 与单测复用）。 */
    public record SnapshotWithLineage(RunSnapshot snapshot, SnapshotLineage lineage) {
    }

    @Override
    public RunSnapshot getRun(SnapshotQuery query) {
        return getSnapshotWithLineage(query).snapshot();
    }

    /**
     * FC-03 情景复制：以 {@code baselineRunId + overrides} 触发预测运行服务确定性重算。
     *
     * <p><b>能力未就绪 ⇒ 真实 501</b>：重算端点属详设-09 §十一 H-5（属主卷 07），全仓 0 实现
     * （实测 {@code scenario-copy} 在 data-engine / workspace 侧均无 mapping）。认知域自算情景金额
     * 直接违反 B-2 金额唯一来源铁律，故此处<b>不</b>降级、不 mock、不返回伪 runId。</p>
     */
    @Override
    public String copyScenario(ScenarioCopyRequest request) {
        String baselineRunId = request == null ? null : request.baselineRunId();
        if (baselineRunId == null || baselineRunId.isBlank()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.E400_INVALID_PARAM + ": baselineRunId 必填（FC-03 情景复制定位键缺失）");
        }
        throw new CognitiveGuardException(CognitiveErrorCode.E503_NOT_IMPLEMENTED,
                CognitiveErrorCode.MSG_NOT_IMPLEMENTED,
                "09册FC-03 scenario-copy 确定性重算端点未上线（期望 "
                        + String.format(SCENARIO_COPY_PATH, baselineRunId) + "，属主详设-09 §十一 H-5 卷07）");
    }

    // ────────────────────────────────────────────────────────────
    // 读通道
    // ────────────────────────────────────────────────────────────

    /** 读快照 + 六要素增项（{@link #getRun} 的完整形态，供编排面取 {@code reusedFromRunId}）。 */
    public SnapshotWithLineage getSnapshotWithLineage(SnapshotQuery query) {
        if (query == null || isBlank(query.forecastRunId())) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.E400_INVALID_PARAM
                            + ": forecastRunId 必填（快照定位六要素缺首键，禁全库扫描）");
        }
        if (!isBlank(query.departmentId())) {
            // 读通道只按 runId 定位；带部门限定时禁静默放宽范围后回全量（要点 4）
            throw new CognitiveGuardException(CognitiveErrorCode.E503_NOT_IMPLEMENTED,
                    CognitiveErrorCode.MSG_NOT_IMPLEMENTED,
                    "data-engine 快照读通道暂不支持 departmentId 限定（属主 H-3/H-5）");
        }
        String runId = query.forecastRunId().trim();
        JsonNode data = fetchSnapshotData(runId);

        String caliberId = text(data, "caliberId");
        String caliberVersion = text(data, "caliberVersion");
        assertCaliberLocked(query, caliberId, caliberVersion);

        List<Cell> cells = readCells(data.get("cells"), query.projectId());
        List<ContributionEdgeSource> edges = readEdges(data.get("edges"));

        RunSnapshot snapshot = new RunSnapshot(
                orDefault(text(data, "forecastRunId"), runId),
                caliberId,
                caliberVersion,
                text(data, "asOfTime"),
                decimal(data, "baselineProfit"),
                decimal(data, "scenarioProfit"),
                cells,
                edges);
        SnapshotLineage lineage = new SnapshotLineage(runId,
                text(data, "snapshotId"),
                text(data, "formulaVersion"),
                text(data, "reusedFromRunId"),
                text(data, "scenarioId"),
                text(data, "runType"),
                text(data, "status"),
                text(data, "baselineRunId"));
        log.info("预测运行快照读取完成: runId={} cells={} edges={} baseline={} scenario={}",
                runId, cells.size(), edges.size(), snapshot.baselineProfit(), snapshot.scenarioProfit());
        return new SnapshotWithLineage(snapshot, lineage);
    }

    /** 幂等复用路径：返回本次快照 + 被复用的基准 run 键（缺列历史行 ⇒ {@code reused=false}，禁伪造）。 */
    public SnapshotLineage getLineage(SnapshotQuery query) {
        return getSnapshotWithLineage(query).lineage();
    }

    private JsonNode fetchSnapshotData(String runId) {
        String base = endpointResolver
                .resolve(DATA_OWNER_SERVICE, ServiceEndpointResolver.RouteMode.MONOLITH)
                .orElseThrow(() -> new CognitiveGuardException(CognitiveErrorCode.E504_INTERNAL,
                        CognitiveErrorCode.E504_INTERNAL + ": route-manifest 未登记服务 "
                                + DATA_OWNER_SERVICE + "，data-engine 快照读通道基址无法解析（fail-loud）"));
        String url = base + String.format(SNAPSHOT_PATH, enc(runId));
        ResponseEntity<JsonNode> resp;
        try {
            resp = restTemplate.exchange(url, HttpMethod.GET, null, JsonNode.class);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                String body = safeBody(e);
                // 端点存在但运行不存在（404 含业务原文） vs 端点根本未上线（无该路由的 404）
                if (body.contains(RUN_NOT_FOUND_MARKER)) {
                    throw new CognitiveGuardException(CognitiveErrorCode.E404_FORECAST_RUN,
                            CognitiveErrorCode.MSG_FORECAST_RUN_NOT_FOUND, "forecastRunId=" + runId);
                }
                throw new CognitiveGuardException(CognitiveErrorCode.E503_NOT_IMPLEMENTED,
                        CognitiveErrorCode.MSG_NOT_IMPLEMENTED,
                        "09册FC-02 只读快照端点未上线: " + SNAPSHOT_PATH);
            }
            throw new CognitiveGuardException(CognitiveErrorCode.E502_DEPENDENCY_DOWN,
                    CognitiveErrorCode.MSG_DEPENDENCY_DOWN,
                    "data-engine 快照读通道 HTTP " + e.getStatusCode().value() + ": " + url);
        } catch (RestClientException e) {
            log.error("data-engine 快照读通道不可达: url={} err={}", url, e.getMessage());
            throw new CognitiveGuardException(CognitiveErrorCode.E502_DEPENDENCY_DOWN,
                    CognitiveErrorCode.MSG_DEPENDENCY_DOWN, "datanet.forecast-run-snapshot");
        }
        JsonNode body = resp.getBody();
        if (body == null || body.isNull()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E502_DEPENDENCY_DOWN,
                    CognitiveErrorCode.MSG_DEPENDENCY_DOWN, "快照读通道返回空响应体（不可判定，禁按空数据处理）");
        }
        int code = body.path("code").asInt(-1);
        if (code == 404) {
            throw new CognitiveGuardException(CognitiveErrorCode.E404_FORECAST_RUN,
                    CognitiveErrorCode.MSG_FORECAST_RUN_NOT_FOUND, "forecastRunId=" + runId);
        }
        if (code != 0) {
            throw new CognitiveGuardException(CognitiveErrorCode.E502_DEPENDENCY_DOWN,
                    CognitiveErrorCode.MSG_DEPENDENCY_DOWN, "快照读通道裁决失败 code=" + code);
        }
        JsonNode data = body.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E502_DEPENDENCY_DOWN,
                    CognitiveErrorCode.MSG_DEPENDENCY_DOWN, "快照读通道缺 data 节点（code=0 却无载荷）");
        }
        return data;
    }

    /** 要点 4：请求上下文与返回快照必须同口径，禁拿别的口径版本冒充。 */
    private static void assertCaliberLocked(SnapshotQuery query, String caliberId, String caliberVersion) {
        if (!isBlank(query.caliberId()) && !Objects.equals(query.caliberId().trim(), caliberId)) {
            throw new CognitiveGuardException(CognitiveErrorCode.E504_INTERNAL,
                    CognitiveErrorCode.MSG_INTERNAL,
                    "快照口径与请求不一致: requested=" + query.caliberId() + " snapshot=" + caliberId);
        }
        if (!isBlank(query.caliberVersion()) && !Objects.equals(query.caliberVersion().trim(), caliberVersion)) {
            throw new CognitiveGuardException(CognitiveErrorCode.E409_VERSION_STALE,
                    CognitiveErrorCode.MSG_VERSION_STALE,
                    "快照口径版本与请求不一致: requested=" + query.caliberVersion()
                            + " snapshot=" + caliberVersion);
        }
    }

    private static List<Cell> readCells(JsonNode arr, String projectIdFilter) {
        List<Cell> cells = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return cells; // 载荷非结构化 JSON（canonicalBytes 文本形态）⇒ 无明细，由调用方 fail-loud，本层不合成
        }
        for (JsonNode n : arr) {
            String projectId = firstText(n, "projectId", "project_id");
            if (!isBlank(projectIdFilter) && !projectIdFilter.trim().equals(projectId)) {
                continue;
            }
            cells.add(new Cell(projectId,
                    intValue(n, "period"),
                    decimal(n, "baseline"),
                    decimal(n, "scenario")));
        }
        return cells;
    }

    private static List<ContributionEdgeSource> readEdges(JsonNode arr) {
        List<ContributionEdgeSource> edges = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return edges;
        }
        for (JsonNode n : arr) {
            List<CognitiveContractSchema.ChainNodeVO> path = new ArrayList<>();
            JsonNode pathNode = n.path("path");
            if (pathNode.isArray()) {
                for (JsonNode p : pathNode) {
                    path.add(new CognitiveContractSchema.ChainNodeVO(
                            text(p, "node"), text(p, "type")));
                }
            }
            List<String> evidenceRefs = new ArrayList<>();
            JsonNode refs = pick(n, "evidenceRefs", "evidence_refs");
            if (refs.isArray()) {
                for (JsonNode r : refs) {
                    evidenceRefs.add(r.asText());
                }
            }
            JsonNode stat = pick(n, "statSupport", "stat_support");
            CognitiveContractSchema.StatSupportVO statSupport = null;
            if (stat.isObject()) {
                Double pValue = optDouble(stat, "pValue", "p_value");
                statSupport = new CognitiveContractSchema.StatSupportVO(text(stat, "method"), pValue);
            }
            edges.add(new ContributionEdgeSource(
                    firstText(n, "chainId", "chain_id"),
                    path,
                    decimal(n, "baseline"),
                    decimal(n, "scenario"),
                    evidenceRefs,
                    statSupport));
        }
        return edges;
    }

    // ── JSON 取值纪律：snake/camel 双形态兼容；缺失 = null（禁 0 替真值，禁伪造回填）──

    /** 首个存在（非 null）的键值，全部缺失回 missing node（.isArray()/.isObject() 自然为 false）。 */
    private static JsonNode pick(JsonNode node, String... candidates) {
        for (String c : candidates) {
            JsonNode v = node.get(c);
            if (v != null && !v.isNull()) {
                return v;
            }
        }
        return MissingNode.getInstance();
    }

    private static int intValue(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? 0 : v.asInt(0);
    }

    private static Double optDouble(JsonNode node, String... candidates) {
        JsonNode v = pick(node, candidates);
        return (v.isNumber() || v.isTextual()) ? v.asDouble() : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String firstText(JsonNode node, String... candidates) {
        for (String c : candidates) {
            String v = text(node, c);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isNumber() || v.isTextual()) {
            try {
                return new BigDecimal(v.asText()); // 文本原文入 BigDecimal，禁经 double
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String orDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String safeBody(HttpStatusCodeException e) {
        String b = e.getResponseBodyAsString();
        return b == null ? "" : b;
    }

    private static String enc(String runId) {
        return java.net.URLEncoder.encode(runId, java.nio.charset.StandardCharsets.UTF_8);
    }
}
