package com.chinacreator.gzcm.workspace.fc;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分册09 F09-04/05/06/11 · 预测运行编排 REST API（场景层 · 只编排不生产）。
 *
 * <p>端点族 §5.1 #8/9/10/11/13/14/15/16 —— workspace <b>只做编排</b>：</p>
 * <ul>
 *   <li>幂等 runKey 命中 SUCCEEDED 且未 force ⇒ 直接返回 reused=true（§3.2）</li>
 *   <li>SUCCEEDED/FAILED 之后任何写操作一律 reject（C210 应用层红线，场景层就地护栏）</li>
 *   <li>六要素随行落 ledger 记录（C209），响应体携 {@code six} 字段 + {@code cellCount}</li>
 *   <li>金额/区间/回测数字一律由 cognitive-engine 计算后经 data-engine 写通道回贴
 *       {@code ecos_dw.ecos_fc_*}；<b>本服务不写任何金额代码</b>（铁律 §0.6 / R-59① / C215）
 *       —— 生产 RT 接线尚未闭环（详见 §十一【校訂】），本批次 coordinator 语义层 live 可测。</li>
 * </ul>
 *
 * <p>禁旁路（§4.2 尾注）：{@code /export} 与 AI 回答金额均由 data-engine / security-engine
 * 承担 RLS/CLS 通道，本 controller 不缓存、不本地拼 CSV、不自行加总。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/forecast-runs")
public class FcForecastRunController {

    private static final Logger log = LoggerFactory.getLogger(FcForecastRunController.class);

    private final FcRunState state;

    public FcForecastRunController(FcRunState state) {
        this.state = state;
    }

    /** §5.1 #8 {@code createForecastRun} —— 六要素入参 + 四源 overrides，返回 runId + status + reused。 */
    @Operation(operationId = "createForecastRun", summary = "createForecastRun")
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody CreateRunRequest r) {
        if (r.sixIncomplete()) {
            throw new BusinessException(400, "六要素入参不全: runId/caliberId/caliberVersion/asOfTime/snapshotId/formulaVersion 必填");
        }
        if (r.scope().size() == 0) {
            throw new BusinessException(400, "scope 缺 projectId / period / stage / departmentId 时不能创建 run（缺 ⇒ DQ_BLOCK，不静默）");
        }
        String scopeHash = sha256(canonicalScope(r.scope()));
        String overridesHash = sha256(canonicalOverrides(r.overrides()));
        String runKey = r.caliberId() + "@" + r.formulaVersion()
                + "|" + r.asOfTime()
                + "|" + scopeHash
                + "|" + overridesHash;

        FcRunState.RunRow prior = state.latestByRunKey(runKey);
        if (prior != null && FcRunState.SUCCEEDED.equals(prior.status()) && !r.force) {
            String reusedId = FcRunState.newUuid();
            FcRunState.RunRow reusedRow = rowOf(reusedId, r, scopeHash, overridesHash, runKey,
                    FcRunState.SUCCEEDED, true, prior.runId(), null, null, new ArrayList<>(),
                    six(r, reusedId));
            state.put(reusedRow);
            log.info("reused SUCCEEDED run={} → reusedRowId={}", prior.runId(), reusedId);
            return ApiResponse.success(view(reusedRow));
        }
        // 本轮（coordinator 语义离线层）：状态直接落到 SUCCEEDED；生产链路上 SUCCEEDED 由 cognitive
        // 计算完成 + data-engine 写通道承运 + 六要素自检通过后回写，本 controller 只做编排语义展示。
        String runId = FcRunState.newUuid();
        FcRunState.RunRow fresh = rowOf(runId, r, scopeHash, overridesHash, runKey,
                FcRunState.SUCCEEDED, false, null, null, null, new ArrayList<>(),
                six(r, runId));
        state.put(fresh);
        log.info("create run runId={} runKey={}", runId, runKey);
        return ApiResponse.success(view(fresh));
    }

    /** §5.1 #9 {@code getForecastRun} —— 六要素摘要 + status。 */
    @Operation(operationId = "getForecastRun", summary = "getForecastRun")
    @GetMapping("/{runId}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String runId) {
        FcRunState.RunRow r = state.require(runId);
        return ApiResponse.success(view(r));
    }

    /** §5.1 #10 {@code queryForecastResults} —— 明细行下钻（数字走 data-engine 结果表，本层只返回行引用）。 */
    @Operation(operationId = "queryForecastResults", summary = "queryForecastResults")
    @GetMapping("/{runId}/results")
    public ApiResponse<Map<String, Object>> results(@PathVariable String runId,
                                                     @RequestParam(required = false) String metric,
                                                     @RequestParam(required = false) String groupBy,
                                                     @RequestParam(required = false) String projectId,
                                                     @RequestParam(required = false) String departmentId,
                                                     @RequestParam(required = false) String period,
                                                     @RequestParam(required = false) String stage) {
        FcRunState.RunRow r = state.require(runId);
        if (!FcRunState.SUCCEEDED.equals(r.status())) {
            throw new BusinessException(409, "results not ready: status=" + r.status());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", r.runId());
        m.put("six", r.six());
        m.put("cellRefs", r.cellRefs());
        m.put("routingNote", "实际明细经 data-engine 唯一读通道回取（ecos_dw.ecos_fc_result_detail），本层不缓存金额");
        return ApiResponse.success(m);
    }

    /** §5.1 #11 {@code traceForecastEvidence} —— 证据抽屉渲染 API（§4.2 结构）。 */
    @Operation(operationId = "traceForecastEvidence", summary = "traceForecastEvidence")
    @GetMapping("/{runId}/evidence")
    public ApiResponse<Map<String, Object>> evidence(@PathVariable String runId,
                                                      @RequestParam String detailId) {
        FcRunState.RunRow r = state.require(runId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", r.runId());
        m.put("detailId", detailId);
        m.put("six", r.six());
        m.put("sourceTypeRouter", "ACTUAL/PLAN→fact row id+change_version; PROFILE_IMPUTED→profile_key+version; MANUAL_OVERRIDE→assumption_key+approver; COMPUTED→child detailIds[]");
        return ApiResponse.success(m);
    }

    /** §5.1 #12 {@code exportForecastRun} —— 走 SEC-01 同管道（本层转 security-engine 端点，不本地拼 CSV）。 */
    @Operation(operationId = "exportForecastRun", summary = "exportForecastRun")
    @GetMapping("/{runId}/export")
    public ApiResponse<Map<String, Object>> export(@PathVariable String runId, @RequestParam(required = false) String format) {
        FcRunState.RunRow r = state.require(runId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", r.runId());
        m.put("format", format == null ? "csv" : format);
        m.put("routingNote", "导出走 SEC-01 security-engine 三通道（RLS/CLS/脱敏），本层禁旁路（§4.2 尾部）");
        return ApiResponse.success(m);
    }

    /** §5.1 #13 {@code copyForecastScenario} —— 情景 copy（COG-03 overrides 结构独立走 R-63 approve 版路径）。 */
    @Operation(operationId = "copyForecastScenario", summary = "copyForecastScenario")
    @PostMapping("/{runId}/scenario-copy")
    public ApiResponse<Map<String, Object>> scenarioCopy(@PathVariable String runId,
                                                          @RequestBody(required = false) Map<String, Object> overrides) {
        FcRunState.RunRow base = state.require(runId);
        if (!FcRunState.SUCCEEDED.equals(base.status())) {
            throw new BusinessException(409, "scenario-copy requires SUCCEEDED base run (C210)");
        }
        Map<String, Object> six0 = base.six();
        CreateRunRequest q = new CreateRunRequest(
                (String) six0.get("caliberId"),
                (String) six0.get("caliberVersion"),
                (String) six0.get("formulaVersion"),
                (String) six0.get("asOfTime"),
                (String) six0.get("snapshotId"),
                Map.of("scope-hash-source", base.scopeHash() == null ? "" : "inherited"),
                overrides == null ? new LinkedHashMap<>() : new LinkedHashMap<>(overrides),
                false);
        String newRunId = FcRunState.newUuid();
        String scopeHash = sha256(canonicalScope(q.scope()));
        String overHash = sha256(canonicalOverrides(q.overrides()));
        String newRunKey = q.caliberId() + "@" + q.formulaVersion() + "|" + q.asOfTime() + "|" + scopeHash + "|" + overHash;
        FcRunState.RunRow newRow = rowOf(newRunId, q, scopeHash, overHash, newRunKey,
                FcRunState.SUCCEEDED, false, base.runId(), null, null, new ArrayList<>(),
                six(q, newRunId));
        state.put(newRow);
        Map<String, Object> m = view(newRow);
        m.put("baselineRunId", base.runId());
        m.put("nonTargetCellsDiffZero", "生产链路由 cognitive 计算后 assertThreeLevelBalance/nonTargetDiff 校验锁线");
        return ApiResponse.success(m);
    }

    /** §5.1 #14 {@code compareForecastRuns} —— 后端算差值（本层不拼数字，行级序号回给 cognitive 处理）。 */
    @Operation(operationId = "compareForecastRuns", summary = "compareForecastRuns")
    @GetMapping("/compare")
    public ApiResponse<Map<String, Object>> compare(@RequestParam String baselineRunId,
                                                     @RequestParam String scenarioRunId,
                                                     @RequestParam(required = false) String groupBy) {
        FcRunState.RunRow a = state.require(baselineRunId);
        FcRunState.RunRow b = state.require(scenarioRunId);
        if (!FcRunState.SUCCEEDED.equals(a.status()) || !FcRunState.SUCCEEDED.equals(b.status())) {
            throw new BusinessException(409, "compare requires both SUCCEEDED");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("baseline", a.six());
        m.put("scenario", b.six());
        m.put("groupBy", groupBy);
        m.put("deltaRouting", "生产链路：cognitive 依 (六要素 + overrides diff) 出差值明细，本层不拼金额");
        return ApiResponse.success(m);
    }

    /** §5.1 #15 {@code getAuditPack} —— 六要素 + 状态迁移序列 + 审计事件指针（Kafka ecos.audit 单源）。 */
    @Operation(operationId = "getForecastAuditPack", summary = "getAuditPack")
    @GetMapping("/{runId}/audit-pack")
    public ApiResponse<Map<String, Object>> auditPack(@PathVariable String runId) {
        FcRunState.RunRow r = state.require(runId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", r.runId());
        m.put("six", r.six());
        m.put("status", r.status());
        m.put("auditChannel", "kafka-topic:ecos.audit (ST06)；游标详情 sysman 侧的 audit facade，本层不缓存");
        return ApiResponse.success(m);
    }

    /** §5.1 #16 {@code retryForecastRun} —— C210 应用层拒改的护栏入口（终态 → reject；否则从 DATA_CHECK 重入）。 */
    @Operation(operationId = "retryForecastRun", summary = "retryForecastRun")
    @PostMapping("/{runId}/retry")
    public ApiResponse<Map<String, Object>> retry(@PathVariable String runId) {
        FcRunState.RunRow r = state.require(runId);
        if (FcRunState.isTerminal(r.status())) {
            throw new BusinessException(409, "C210 immutability: terminal run cannot retry; run a new run with force=true (new runKey intentional)");
        }
        state.transition(runId, FcRunState.DATA_CHECK);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", runId);
        m.put("status", FcRunState.DATA_CHECK);
        return ApiResponse.success(m);
    }

    // ── 私有辅助 ──

    private static FcRunState.RunRow rowOf(String runId, CreateRunRequest r,
                                  String scopeHash, String overridesHash, String runKey,
                                  String status, boolean reused, String reusedFromRunId,
                                  String errorCode, String errorText,
                                  List<Object> cellRefs, Map<String, Object> six) {
        return new FcRunState.RunRow(runId, six, status, reused, reusedFromRunId,
                null, scopeHash, overridesHash, runKey, errorCode, errorText, cellRefs,
                auditPackStub(runId, six, status));
    }

    private static Map<String, Object> six(CreateRunRequest r, String runId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("forecastRunId", runId);
        m.put("caliberId", r.caliberId());
        m.put("caliberVersion", r.caliberVersion());
        m.put("asOfTime", r.asOfTime());
        m.put("snapshotId", r.snapshotId());
        m.put("formulaVersion", r.formulaVersion());
        return m;
    }

    private static Map<String, Object> auditPackStub(String runId, Map<String, Object> six, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("runId", runId);
        e.put("type", "run_created");
        e.put("status", status);
        e.put("channel", "kafka-topic:ecos.audit");
        m.put("events", List.of(e));
        m.put("six", six);
        return m;
    }

    private static Map<String, Object> view(FcRunState.RunRow r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", r.runId());
        m.put("status", r.status());
        m.put("reused", r.reused());
        m.put("reusedFromRunId", r.reusedFromRunId());
        m.put("taskId", r.taskId());
        m.put("scopeHash", r.scopeHash());
        m.put("overridesHash", r.overridesHash());
        m.put("runKey", r.runKey());
        m.put("errorCode", r.errorCode());
        m.put("errorText", r.errorText());
        m.put("six", r.six());
        m.put("cellCount", r.cellRefs() == null ? 0 : r.cellRefs().size());
        return m;
    }

    private static String canonicalScope(Map<String, Object> scope) {
        if (scope == null || scope.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (var e : new java.util.TreeMap<>(scope).entrySet()) {
            Object v = e.getValue();
            if (v instanceof Iterable<?> it) {
                List<String> xs = new ArrayList<>();
                for (Object o : it) xs.add(String.valueOf(o));
                xs.sort(Comparator.naturalOrder());
                parts.add(e.getKey() + "=" + String.join(",", xs));
            } else {
                parts.add(e.getKey() + "=" + v);
            }
        }
        return String.join(";", parts);
    }

    private static String canonicalOverrides(Map<String, Object> ov) {
        if (ov == null || ov.isEmpty()) return "";
        java.util.TreeMap<String, Object> sorted = new java.util.TreeMap<>(ov);
        StringBuilder sb = new StringBuilder("{");
        boolean firstT = true;
        for (var e : sorted.entrySet()) {
            if (!firstT) sb.append(',');
            firstT = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    private static String sha256(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest((s == null ? "" : s).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 六要素入参物（§5.1 #8 createForecastRun）。六要素缺失即 400，不静默默认（C209）。 */
    public record CreateRunRequest(String caliberId, String caliberVersion, String formulaVersion,
                                    String asOfTime, String snapshotId,
                                    Map<String, Object> scope,
                                    Map<String, Object> overrides,
                                    boolean force) {
        public boolean sixIncomplete() {
            return isBlank(caliberId) || isBlank(caliberVersion) || isBlank(formulaVersion)
                    || isBlank(asOfTime) || isBlank(snapshotId);
        }
        private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    }
}
