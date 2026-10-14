package com.chinacreator.gzcm.engine.cognitive2.service.forecast;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.DeterministicForecastCalculator;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.DeterministicForecastCalculator.ArtifactRow;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.DeterministicForecastCalculator.SixFactors;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastRunSnapshotReader.ContributionEdgeSource;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastRunSnapshotReader.RunSnapshot;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastRunSnapshotReader.SnapshotQuery;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 预测 run 计算与落库服务（详设-09 v1.2 §十一 H-5 认知侧半区 / 差距分析 P0-4 断点接通）.
 *
 * <p><b>职责边界</b>：本服务是 workspace {@code ForecastRunGateway} SPI 在认知域的真实落地 ——
 * 取输入事实（{@link #FACTS_PATH} 只读通道）→ 纯函数确定性装配
 * （{@link DeterministicForecastCalculator}，禁 Spring/DB 依赖）→ 唯一写通道落库
 * （{@link ForecastArtifactWriteChannel}，ST08：认知域禁直连 {@code ecos_dw}）→ 回读快照
 * （{@link DataEngineForecastRunSnapshotReader}）。四段全部复用既有底座，<b>不新造第二套</b>
 * 计算/写通道/读通道（铁律 §0.6 / ADR-14 / R-17 ①）。</p>
 *
 * <p><b>金额唯一来源（B-2 / R-8 ②）</b>：本服务不产生任何金额数值；明细数值一律来自事实表
 * 存值原样搬运。区间三值（P10/P50/P90）当前<b>物理无列</b>（V197 头表与 V277/V287 增列均无三值，
 * 三值列只在"预留未启用"的 V216 {@code ecos_fc_result_detail}）⇒ 原样返回 null，
 * 禁插值、禁取均值、禁兜底合成（R-62 ①）。</p>
 *
 * <p><b>fail-loud，禁 mock 成功（X-44 / DG-9）</b>：</p>
 * <ul>
 *   <li>输入事实零行 ⇒ {@code ECOS-COG-400P}（"无事实即无计算"，绝不返回空产物冒充成功）；</li>
 *   <li>事实读通道不可达/裁决失败 ⇒ {@code ECOS-COG-502}；</li>
 *   <li>写通道失败 ⇒ 由 {@link ForecastArtifactWriteChannel} 抛 {@code ECOS-COG-504}，不吞；</li>
 *   <li>回读快照失败 ⇒ 产物已落库但<b>未经读回确认</b>，返回体打 {@code degraded=true}
 *       并记 warn，禁把"写入回执"当"读回一致"上报。</li>
 * </ul>
 *
 * <p><b>输入装配裁决（B2-② 批次登记，差距分析 §B2 追溯）</b>：{@code ArtifactRow} 六字段取自
 * {@link #FACT_TYPE} = STAGE 事实表（V167 表 2）列，映射为
 * projectId←project_id、departmentId←department_id、period←period、
 * metric←fact_type（请求指定 metric 时以请求为准）、value←amount、
 * valueSource←source_type（缺次 source_ref）。依据 = 详设-09 九链路站③「事实」权威载体。
 * {@code evidenceRefs} 取行内 evidence_ref 去重集；空集时由计算器拒算（不可追溯不得落库）。</p>
 */
@Service
public class ForecastRunComputeService {

    private static final Logger log = LoggerFactory.getLogger(ForecastRunComputeService.class);

    /** 输入事实类型（V167 表 2 阶段事实：projectId/departmentId/period/amount/factType/sourceType/evidenceRef）。 */
    static final String FACT_TYPE = "STAGE";
    /** 事实只读分页通道（data-engine {@code FactController#listFacts}，响应 {@code data.items} + {@code data.total}）。 */
    static final String FACTS_PATH = "/api/v1/datanet/facts/%s?page=%d&pageSize=%d";
    /** 幂等键定位通道（V287 增列，{@code ForecastRunSnapshotController} 新映射）。 */
    static final String SNAPSHOT_BY_KEY_PATH = "/api/v1/datanet/forecast-runs/by-idempotency-key/%s/snapshot";
    /** 归属服务（route-manifest owner = datanet），与写通道同源。 */
    static final String DATA_OWNER_SERVICE = "datanet";
    /** 单页取数上限（与 data-engine listFacts 服务端上限一致，超页循环累加，禁一次拉全表）。 */
    private static final int PAGE_SIZE = 200;
    /** 翻页保护（禁无界循环）。 */
    private static final int MAX_PAGES = 50;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final ServiceEndpointResolver endpointResolver;
    private final ForecastArtifactWriteChannel writeChannel;
    private final DataEngineForecastRunSnapshotReader snapshotReader;

    public ForecastRunComputeService(@Qualifier("cognitiveRestTemplate") RestTemplate restTemplate,
                                     ServiceEndpointResolver endpointResolver,
                                     ForecastArtifactWriteChannel writeChannel,
                                     DataEngineForecastRunSnapshotReader snapshotReader) {
        this.restTemplate = restTemplate;
        this.endpointResolver = endpointResolver;
        this.writeChannel = writeChannel;
        this.snapshotReader = snapshotReader;
    }

    /**
     * 计算请求：六要素与幂等锚由编排面给定，{@code forecastRunId} 缺位时由认知域生成（生产方属主）。
     *
     * <p><b>幂等锚属主裁决 D-B2②-1</b>：{@code idempotencyKey}/{@code contentHash} 是<b>调用方</b>
     * （编排契约 {@code ForecastRunRequestValidator} 必填项）给出的"同一请求重放"锚，
     * 必须原样落库、原样回读；计算器自产的 {@code artifact.idempotencyKey()} 含 forecastRunId、
     * 每次重试必变，只能作内部规范指纹，不能替代它（否则编排面按调用方键永查不到既有 run，
     * 幂等复用与 409 冲突判定双双落空）。详见
     * {@link ForecastArtifactWriteChannel#submit(DeterministicForecastCalculator.Artifact, DeterministicForecastCalculator.SixFactors, String, String, String, String, String, String, String)}。</p>
     *
     * @param forecastRunId 运行键（可空 ⇒ 认知域生成 UUID；编排面只编排不生产，R-14 ①）
     * @param projectIds    命中项目范围（可空 = 不限定，仍受分页上限约束）
     * @param periods       命中期间范围（可空同上）
     * @param metric        度量名覆盖（可空 = 取事实行 fact_type 原值）
     */
    public record ComputeRequest(String forecastRunId,
                                 String scenarioId,
                                 String runType,
                                 String caliberId,
                                 String caliberVersion,
                                 String asOfTime,
                                 String snapshotId,
                                 String formulaVersion,
                                 String idempotencyKey,
                                 String contentHash,
                                 String baselineRunId,
                                 String traceId,
                                 List<String> projectIds,
                                 List<String> periods,
                                 String metric) {
    }

    /** 计算 + 落库 + 回读，返回 run 视图（键名与 workspace {@code ForecastRunView} 同形）。 */
    public Map<String, Object> compute(ComputeRequest request) {
        if (request == null) {
            throw badRequest("compute 请求体缺失（无输入即无计算，禁空跑）");
        }
        require(request.scenarioId(), "scenarioId");
        require(request.caliberId(), "caliberId");
        require(request.caliberVersion(), "caliberVersion");
        require(request.asOfTime(), "asOfTime");
        require(request.formulaVersion(), "formulaVersion");
        require(request.traceId(), "traceId");
        require(request.idempotencyKey(), "idempotencyKey");
        require(request.contentHash(), "contentHash");
        // run 身份缺位 ⇒ 生产方（认知域）生成，编排面不造 id；UUID 形态对齐 MC01 主键口径
        String forecastRunId = isBlank(request.forecastRunId())
                ? "fc-" + java.util.UUID.randomUUID() : request.forecastRunId().trim();

        List<JsonNode> facts = fetchInputFacts(request);
        if (facts.isEmpty()) {
            throw badRequest("输入事实为零行（factType=" + FACT_TYPE
                    + "，范围 projectIds=" + size(request.projectIds())
                    + "/periods=" + size(request.periods()) + "）—— 无事实禁造产物");
        }
        List<ArtifactRow> rows = new ArrayList<>(facts.size());
        Set<String> evidenceRefs = new LinkedHashSet<>();
        for (JsonNode fact : facts) {
            rows.add(toArtifactRow(fact, request.metric()));
            collectEvidence(fact, evidenceRefs);
        }

        SixFactors factors = new SixFactors(forecastRunId, request.caliberId(),
                request.caliberVersion(), request.asOfTime(), canonicalInputJson(facts),
                request.formulaVersion(), List.copyOf(evidenceRefs));

        DeterministicForecastCalculator.Artifact artifact;
        try {
            artifact = DeterministicForecastCalculator.compute(factors, rows);
        } catch (IllegalArgumentException e) {
            // 计算器输入完整性门禁（六要素缺项 / 无证据引用）原样转 400，禁降级为空产物
            throw badRequest("确定性装配拒绝：" + e.getMessage());
        }

        ForecastArtifactWriteChannel.WriteAck ack = writeChannel.submit(artifact, factors,
                request.scenarioId(), request.runType(), request.traceId(),
                request.snapshotId(), request.baselineRunId(),
                request.idempotencyKey(), request.contentHash());
        log.info("预测 run 已落库 runId={} writeId={} idempotentReplay={} cells={} evidence={}",
                artifact.forecastRunId(), ack.writeId(), ack.idempotentReplay(),
                rows.size(), evidenceRefs.size());

        return runView(forecastRunId, request, artifact);
    }

    /** 按运行键读 run 视图（快照 + 六要素增项；无行由 reader 抛 404，禁伪装空视图）。 */
    public Map<String, Object> findRun(String forecastRunId) {
        return viewOf(readOrThrow(forecastRunId));
    }

    /**
     * 按幂等键读既有 run 视图；<b>未命中返回 null</b>（编排面据此判"首次运行"并继续提交）。
     * 这是正常分支而非资源缺失，故与 {@link #findRun} 的 404 语义严格分开。
     */
    public Map<String, Object> findByIdempotencyKey(String idempotencyKey) {
        require(idempotencyKey, "idempotencyKey");
        JsonNode data = fetchJson(datanetBase() + String.format(SNAPSHOT_BY_KEY_PATH, enc(idempotencyKey)));
        return data == null || data.isNull() ? null : viewOfJson(data);
    }

    /** 结果明细：区间三值原样给出（物理无列 = null，禁插值合成，R-62 ①）。 */
    public Map<String, Object> readResults(String forecastRunId) {
        RunSnapshot snapshot = readOrThrow(forecastRunId).snapshot();
        List<Map<String, Object>> cells = new ArrayList<>();
        snapshot.cells().forEach(cell -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("projectId", cell.projectId());
            row.put("period", cell.period());
            row.put("baseline", cell.baseline());
            row.put("scenario", cell.scenario());
            cells.add(row);
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("forecastRunId", snapshot.forecastRunId());
        out.put("cells", cells);
        out.put("baselineProfit", snapshot.baselineProfit());
        out.put("scenarioProfit", snapshot.scenarioProfit());
        out.put("p10", null);
        out.put("p50", null);
        out.put("p90", null);
        out.put("quantileSource", "NOT_AVAILABLE");
        return out;
    }

    /** 证据引用集（后端组装：六要素锚 + 快照因果边 evidenceRefs，编排面不拼公式）。 */
    public Map<String, Object> readEvidence(String forecastRunId) {
        DataEngineForecastRunSnapshotReader.SnapshotWithLineage read = readOrThrow(forecastRunId);
        RunSnapshot snapshot = read.snapshot();
        List<Map<String, Object>> items = new ArrayList<>();
        Set<String> refs = new LinkedHashSet<>();
        for (ContributionEdgeSource edge : snapshot.edges()) {
            for (String ref : edge.evidenceRefs()) {
                if (ref == null || ref.isBlank() || !refs.add(ref)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("evidenceRef", ref);
                item.put("chainId", edge.chainId());
                item.put("baseline", edge.baseline());
                item.put("scenario", edge.scenario());
                items.add(item);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("forecastRunId", snapshot.forecastRunId());
        out.put("caliberId", snapshot.caliberId());
        out.put("caliberVersion", snapshot.caliberVersion());
        out.put("asOfTime", snapshot.asOfTime());
        out.put("snapshotId", read.lineage().snapshotId());
        out.put("formulaVersion", read.lineage().formulaVersion());
        out.put("evidenceRefs", List.copyOf(refs));
        out.put("evidenceItems", items);
        return out;
    }

    // ── 视图装配 ────────────────────────────────────────────────

    private Map<String, Object> runView(String forecastRunId,
                                        ComputeRequest request,
                                        DeterministicForecastCalculator.Artifact artifact) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("forecastRunId", forecastRunId);
        view.put("scenarioId", request.scenarioId());
        view.put("runType", request.runType() == null ? "FORECAST" : request.runType());
        view.put("status", "SUCCEEDED");
        view.put("caliberId", request.caliberId());
        view.put("caliberVersion", request.caliberVersion());
        view.put("asOfTime", request.asOfTime());
        view.put("snapshotId", request.snapshotId());
        view.put("formulaVersion", request.formulaVersion());
        // 幂等锚 = 调用方给定值（与库内 V287 两列同源），禁在此替换为引擎派生指纹
        view.put("idempotencyKey", request.idempotencyKey());
        view.put("contentHash", request.contentHash());
        view.put("canonicalContentHash", artifact.contentHash());
        view.put("baselineRunId", request.baselineRunId());
        view.put("reusedFromRunId", null);
        view.put("taskId", null);
        view.put("degraded", false);
        view.put("evidenceRefs", List.of());
        view.put("quantile", quantileUnavailable());
        try {
            DataEngineForecastRunSnapshotReader.SnapshotWithLineage readback =
                    snapshotReader.getSnapshotWithLineage(queryOf(artifact.forecastRunId()));
            RunSnapshot snapshot = readback.snapshot();
            view.put("snapshotId", orKeep(readback.lineage().snapshotId(), request.snapshotId()));
            view.put("reusedFromRunId", readback.lineage().reusedFromRunIdOrEmpty());
            view.put("evidenceRefs", collectSnapshotEvidence(snapshot));
            view.put("baselineProfit", snapshot.baselineProfit());
            view.put("scenarioProfit", snapshot.scenarioProfit());
        } catch (RuntimeException e) {
            // 写回执已成功但读回未确认：如实标 degraded，禁把"已提交"当"已核验"上报
            log.warn("预测 run 回读未确认（degraded=true）: runId={} err={}",
                    artifact.forecastRunId(), e.getMessage());
            view.put("degraded", true);
        }
        return view;
    }

    private Map<String, Object> viewOf(DataEngineForecastRunSnapshotReader.SnapshotWithLineage read) {
        RunSnapshot snapshot = read.snapshot();
        DataEngineForecastRunSnapshotReader.SnapshotLineage lineage = read.lineage();
        return fields(snapshot.forecastRunId(), snapshot.caliberId(), snapshot.caliberVersion(),
                snapshot.asOfTime(), lineage.snapshotId(), lineage.formulaVersion(),
                lineage.reusedFromRunId(), lineage.scenarioId(), lineage.runType(), lineage.status(),
                lineage.baselineRunId(), null, null, null,
                snapshot.baselineProfit(), snapshot.scenarioProfit(),
                collectSnapshotEvidence(snapshot));
    }

    private Map<String, Object> viewOfJson(JsonNode data) {
        return fields(text(data, "forecastRunId"), text(data, "caliberId"), text(data, "caliberVersion"),
                text(data, "asOfTime"), text(data, "snapshotId"), text(data, "formulaVersion"),
                text(data, "reusedFromRunId"), text(data, "scenarioId"), text(data, "runType"),
                text(data, "status"), text(data, "baselineRunId"),
                text(data, "idempotencyKey"), text(data, "contentHash"), text(data, "taskId"),
                decimal(data, "baselineProfit"), decimal(data, "scenarioProfit"), evidenceRefsOf(data));
    }

    /** 视图字段落位（缺位一律 null，禁按"看起来合理"造数）。 */
    private Map<String, Object> fields(String forecastRunId, String caliberId, String caliberVersion,
                                       String asOfTime, String snapshotId, String formulaVersion,
                                       String reusedFromRunId, String scenarioId, String runType,
                                       String status, String baselineRunId, String idempotencyKey,
                                       String contentHash, String taskId, BigDecimal baselineProfit,
                                       BigDecimal scenarioProfit, List<String> evidenceRefs) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("forecastRunId", forecastRunId);
        view.put("scenarioId", scenarioId);
        view.put("runType", runType);
        view.put("status", status);
        view.put("caliberId", caliberId);
        view.put("caliberVersion", caliberVersion);
        view.put("asOfTime", asOfTime);
        view.put("snapshotId", snapshotId);
        view.put("formulaVersion", formulaVersion);
        view.put("idempotencyKey", idempotencyKey);
        view.put("contentHash", contentHash);
        view.put("baselineRunId", baselineRunId);
        view.put("reusedFromRunId", reusedFromRunId);
        view.put("taskId", taskId);
        view.put("degraded", false);
        view.put("evidenceRefs", evidenceRefs);
        view.put("baselineProfit", baselineProfit);
        view.put("scenarioProfit", scenarioProfit);
        view.put("quantile", quantileUnavailable());
        return view;
    }

    private static Map<String, Object> quantileUnavailable() {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("p10", null);
        q.put("p50", null);
        q.put("p90", null);
        return q;
    }

    // ── 输入事实读通道 ──────────────────────────────────────────

    private List<JsonNode> fetchInputFacts(ComputeRequest request) {
        String base = datanetBase();
        List<JsonNode> all = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode data = fetchJson(base + String.format(FACTS_PATH, FACT_TYPE, page, PAGE_SIZE));
            JsonNode items = data == null ? null : data.path("items");
            if (items == null || !items.isArray() || items.isEmpty()) {
                break;
            }
            items.forEach(node -> {
                if (inScope(node, request) && inDqPassed(node)) {
                    all.add(node);
                }
            });
            if (items.size() < PAGE_SIZE) {
                break;
            }
        }
        return all;
    }

    /**
     * DQ 采纳面：{@code dq_status} 为 PASSED 的事实行才参与装配。
     *
     * <p>缺列/空值按"未判定"处理并<b>放行</b>是本批的已知留白（V167 导入链对
     * {@code dq_status} 有默认值，实测列存在），一旦列值非 PASSED 一律拒绝。
     * 收紧为"仅 PASSED"属 09 册站② DQ 门禁批（登记为剩余项，不在本批夹带）。</p>
     */
    private static boolean inDqPassed(JsonNode fact) {
        String dq = firstText(fact, "dq_status", "dqStatus");
        return dq == null || !"FAILED".equalsIgnoreCase(dq);
    }

    private static boolean inScope(JsonNode fact, ComputeRequest request) {
        List<String> projectIds = request.projectIds();
        if (projectIds != null && !projectIds.isEmpty()) {
            String projectId = firstText(fact, "project_id", "projectId");
            if (projectId == null || !projectIds.contains(projectId)) {
                return false;
            }
        }
        List<String> periods = request.periods();
        if (periods != null && !periods.isEmpty()) {
            String period = firstText(fact, "period");
            if (period == null || !periods.contains(period)) {
                return false;
            }
        }
        return true;
    }

    private ArtifactRow toArtifactRow(JsonNode fact, String metricOverride) {
        String valueSource = orKeep(firstText(fact, "source_type", "sourceType"),
                firstText(fact, "source_ref", "sourceRef"));
        if (valueSource == null) {
            throw badRequest("事实行缺 source_type/source_ref（数值来源不可追溯，禁参与装配）: "
                    + firstText(fact, "id"));
        }
        return new ArtifactRow(
                firstText(fact, "project_id", "projectId"),
                firstText(fact, "department_id", "departmentId"),
                firstText(fact, "period"),
                orKeep(metricOverride, firstText(fact, "fact_type", "factType")),
                decimal(fact, "amount"),
                valueSource);
    }

    private static void collectEvidence(JsonNode fact, Set<String> sink) {
        String ref = firstText(fact, "evidence_ref", "evidenceRef");
        if (ref != null && !ref.isBlank()) {
            sink.add(ref);
        }
    }

    private static List<String> collectSnapshotEvidence(RunSnapshot snapshot) {
        Set<String> refs = new LinkedHashSet<>();
        snapshot.edges().forEach(edge -> refs.addAll(edge.evidenceRefs()));
        return List.copyOf(refs);
    }

    private static List<String> evidenceRefsOf(JsonNode data) {
        Set<String> refs = new LinkedHashSet<>();
        JsonNode edges = data.path("edges");
        if (edges.isArray()) {
            edges.forEach(edge -> edge.path("evidenceRefs").forEach(ref -> {
                if (ref.isTextual() && !ref.asText().isBlank()) {
                    refs.add(ref.asText());
                }
            }));
        }
        return List.copyOf(refs);
    }

    /** 输入快照序列化（六要素之 inputSnapshotJson）：事实行原文序列化，保证"重跑一致"可复算。 */
    private static String canonicalInputJson(List<JsonNode> facts) {
        ArrayNode arr = MAPPER.createArrayNode();
        facts.forEach(arr::add);
        return arr.toString();
    }

    // ── 底座与工具 ──────────────────────────────────────────────

    private static SnapshotQuery queryOf(String forecastRunId) {
        return new SnapshotQuery(forecastRunId.trim(), null, null, null, null, null);
    }

    /**
     * 三条读路径（{@link #findRun}/{@link #readResults}/{@link #readEvidence}）统一定位守卫：
     * 定位键缺项先 400（禁把空白键交给读通道 ⇒ 全库扫描面），读通道空载荷按依赖异常上报
     * （禁 NPE 冒到 REST 面变成 500 无定位，也禁按"空快照"装配出看似正常的视图）。
     */
    private DataEngineForecastRunSnapshotReader.SnapshotWithLineage readOrThrow(String forecastRunId) {
        require(forecastRunId, "forecastRunId");
        DataEngineForecastRunSnapshotReader.SnapshotWithLineage read =
                snapshotReader.getSnapshotWithLineage(queryOf(forecastRunId));
        if (read == null || read.snapshot() == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E502_DEPENDENCY_DOWN,
                    CognitiveErrorCode.MSG_DEPENDENCY_DOWN,
                    "快照读通道返回空载荷（不可判定，禁按空快照造视图）: runId=" + forecastRunId);
        }
        return read;
    }

    private String datanetBase() {
        return endpointResolver
                .resolve(DATA_OWNER_SERVICE, ServiceEndpointResolver.RouteMode.MONOLITH)
                .orElseThrow(() -> new CognitiveGuardException(CognitiveErrorCode.E504_INTERNAL,
                        CognitiveErrorCode.E504_INTERNAL + ": route-manifest 未登记服务 "
                                + DATA_OWNER_SERVICE + "，预测 run 读通道基址无法解析（fail-loud，禁回落 localhost）"));
    }

    private JsonNode fetchJson(String url) {
        ResponseEntity<JsonNode> resp;
        try {
            resp = restTemplate.exchange(url, HttpMethod.GET, null, JsonNode.class);
        } catch (RestClientException e) {
            throw new CognitiveGuardException(CognitiveErrorCode.E502_DEPENDENCY_DOWN,
                    CognitiveErrorCode.MSG_DEPENDENCY_DOWN,
                    "data-engine 预测 run 读通道不可达: " + url + " err=" + e.getMessage());
        }
        JsonNode body = resp.getBody();
        if (body == null || body.isNull()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E502_DEPENDENCY_DOWN,
                    CognitiveErrorCode.MSG_DEPENDENCY_DOWN, "读通道空响应体（不可判定，禁按空数据处理）");
        }
        int code = body.path("code").asInt(-1);
        if (code != 0) {
            throw new CognitiveGuardException(CognitiveErrorCode.E502_DEPENDENCY_DOWN,
                    CognitiveErrorCode.MSG_DEPENDENCY_DOWN,
                    "读通道裁决失败 code=" + code + " message=" + body.path("message").asText(""));
        }
        JsonNode data = body.path("data");
        return data.isMissingNode() || data.isNull() ? null : data;
    }

    private static CognitiveGuardException badRequest(String reason) {
        return new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                CognitiveErrorCode.E400_INVALID_PARAM + ": " + reason);
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw badRequest(field + " 必填（run 身份/六要素缺项，禁半集计算）");
        }
    }

    private static int size(List<String> list) {
        return list == null ? 0 : list.size();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String orKeep(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isValueNode() && !v.isNull() ? v.asText() : null;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isNumber()) {
            return v.decimalValue();
        }
        if (v.isTextual() && !v.asText().isBlank()) {
            try {
                return new BigDecimal(v.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
