package com.chinacreator.gzcm.workspace.analysis;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunCommand;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunOrchestrationService;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunView;
import com.chinacreator.gzcm.workspace.scenario.AipPipelineRunClient;
import com.chinacreator.gzcm.workspace.scenario.RunMode;
import com.chinacreator.gzcm.workspace.scenario.SandboxWriteGuard;
import com.chinacreator.gzcm.workspace.scenario.ScenarioRunRequestValidator;
import com.chinacreator.gzcm.workspace.scenario.ScenarioRunService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 场景运行门面（2026-11-21 场景工作台简化 v0.3）。
 *
 * <p>统一三种正式分析：CAUSAL（诊断）/ FORECAST（预测）/ SIMULATION（仿真）。
 * 目标：让前端只传业务输入，由服务端补齐预测九要素中的可推导项（幂等键/内容指纹），
 * 缺真实输入即 400；预测结果与定义版本通过 ecos_scenario_run 锚点列统一回读。</p>
 *
 * <p>边界：不重写 {@link ScenarioRunService} / {@link ForecastRunOrchestrationService}；
 * 不改变既有 run/forecast 端点签名；试算（What-If）不走本门面，仍明确标注为探索通道。</p>
 */
@Service
public class ScenarioAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioAnalysisService.class);

    private final WorkspaceDataDao dao;
    private final ScenarioService scenarioService;
    private final ScenarioRunService runService;
    private final ScenarioRunRequestValidator runValidator;
    private final ForecastRunOrchestrationService forecastService;
    private final AipPipelineRunClient pipelineRunClient;
    private final SandboxWriteGuard sandboxWriteGuard;

    public ScenarioAnalysisService(WorkspaceDataDao dao,
                                   ScenarioService scenarioService,
                                   ScenarioRunService runService,
                                   ScenarioRunRequestValidator runValidator,
                                   ForecastRunOrchestrationService forecastService,
                                   AipPipelineRunClient pipelineRunClient) {
        this(dao, scenarioService, runService, runValidator, forecastService, pipelineRunClient,
                new SandboxWriteGuard());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ScenarioAnalysisService(WorkspaceDataDao dao,
                                   ScenarioService scenarioService,
                                   ScenarioRunService runService,
                                   ScenarioRunRequestValidator runValidator,
                                   ForecastRunOrchestrationService forecastService,
                                   AipPipelineRunClient pipelineRunClient,
                                   SandboxWriteGuard sandboxWriteGuard) {
        this.dao = dao;
        this.scenarioService = scenarioService;
        this.runService = runService;
        this.runValidator = runValidator;
        this.forecastService = forecastService;
        this.pipelineRunClient = pipelineRunClient;
        this.sandboxWriteGuard = sandboxWriteGuard;
    }

    /** 场景分析列表（锚点表统一读）。 */
    public List<Map<String, Object>> list(String scenarioId, int limit) {
        scenarioService.get(scenarioId);
        int safe = limit <= 0 || limit > 100 ? 20 : limit;
        return dao.listAnalyses(scenarioId, safe);
    }

    /** 单条分析详情（锚点 + 结果原文）。 */
    public Map<String, Object> get(String analysisId) {
        List<Map<String, Object>> rows = dao.findAnalysisById(analysisId);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "ANALYSIS-404: 分析不存在: " + analysisId);
        }
        return rows.get(0);
    }

    /**
     * 发起正式分析。
     * <p>请求：{@code analysisType=CAUSAL|FORECAST|SIMULATION}，{@code runMode=FORMAL|SANDBOX}，
     * 以及各类型业务输入。预测必备真实口径/时点/快照/公式版本；幂等键/内容指纹由服务端生成。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> run(String scenarioId, Map<String, Object> param) {
        scenarioService.get(scenarioId);
        Map<String, Object> p = param == null ? Map.of() : param;
        String analysisType = upper(p.get("analysisType"));
        if (analysisType == null) {
            throw new BusinessException(400, "ANALYSIS-400: analysisType 必填（CAUSAL|FORECAST|SIMULATION）");
        }
        String runMode = upper(p.get("runMode"));
        RunMode mode = runMode == null ? RunMode.FORMAL : RunMode.parseOrDefault(runMode);
        String[] pointer = definitionPointer(scenarioId);

        return switch (analysisType) {
            case "FORECAST" -> runForecast(scenarioId, p, mode, pointer);
            case "CAUSAL" -> runScenarioRun(scenarioId, p, mode, pointer, "DIAGNOSE", "CAUSAL");
            case "SIMULATION" -> runScenarioRun(scenarioId, p, mode, pointer, "SIMULATE", "SIMULATION");
            case "PIPELINE" -> runPipeline(scenarioId, p, mode, pointer);
            default -> throw new BusinessException(400,
                    "ANALYSIS-400: 非法 analysisType: " + analysisType
                            + "（允许 CAUSAL|FORECAST|SIMULATION|PIPELINE）");
        };
    }

    /**
     * 场景智能逻辑（2026-11-23 承接批）：按场景绑定的逻辑编排 id 调属主 ai-engine 执行。
     *
     * <p><b>取值口径</b>：绑定组 {@code pipelines}（binding_type=PIPELINE）。请求体可显式传
     * {@code pipelineId} 覆盖（多绑定场景选其一）；都没给则 400 —— <b>不猜默认编排</b>。
     * 绑定存在但属主不可达 ⇒ 503（{@link AipPipelineUnavailableException}，fail-closed）。</p>
     *
     * <p><b>落库口径</b>：结果落 {@code ecos_scenario_run} 锚点（analysis_type=PIPELINE，
     * source_type=AIP_PIPELINE，source_id=编排 id），节点轨迹写入 simulation_result 槽位
     * （与 SIMULATE/SAFEGUARD 同槽位语义：非诊断/非预测的结构化执行结果）；
     * 沙箱模式沿用 {@link SandboxWriteGuard} 的写入限制。</p>
     */
    private Map<String, Object> runPipeline(String scenarioId, Map<String, Object> p, RunMode mode,
                                            String[] pointer) {
        String pipelineId = text(p.get("pipelineId"));
        if (pipelineId == null) {
            List<String> bound = scenarioService.bindings(scenarioId).get("pipelines");
            if (bound == null || bound.isEmpty()) {
                throw new BusinessException(400,
                        "ANALYSIS-400: 场景未绑定逻辑编排（binding_type=PIPELINE），请先在场景定义中绑定");
            }
            if (bound.size() > 1) {
                throw new BusinessException(400,
                        "ANALYSIS-400: 场景绑定了 " + bound.size()
                                + " 条编排，请在请求体显式指定 pipelineId");
            }
            pipelineId = bound.get(0);
        }

        Map<String, Object> inputs = new LinkedHashMap<>();
        Object rawInputs = p.get("inputs");
        if (rawInputs instanceof Map<?, ?> m) {
            m.forEach((k, v) -> inputs.put(String.valueOf(k), v));
        }
        Map<String, Object> result = pipelineRunClient.execute(pipelineId, inputs);

        String status = result.get("status") == null ? "UNKNOWN" : String.valueOf(result.get("status"));
        boolean degraded = Boolean.TRUE.equals(result.get("degraded"));
        String anchorId = "run_" + UUID.randomUUID().toString().substring(0, 12);
        sandboxWriteGuard.assertAllowed(mode, "ecos_scenario_run");
        dao.insertRun(anchorId, scenarioId, "PIPELINE",
                degraded ? "SUCCEEDED_DEGRADED" : "SUCCEEDED",
                text(p.get("metric")), p.get("deviation"),
                null, null, toJson(result), null,
                degraded, currentOperator(), mode.name(),
                text(p.get("baselineRunId")),
                mode == RunMode.SANDBOX ? "SANDBOX" : "NONE");
        dao.updateAnalysisAnchorMeta(anchorId, "PIPELINE", "AIP_PIPELINE", pipelineId,
                pointer[0], pointer[1]);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("analysisId", anchorId);
        out.put("scenarioId", scenarioId);
        out.put("analysisType", "PIPELINE");
        out.put("runMode", mode.name());
        out.put("sourceType", "AIP_PIPELINE");
        out.put("sourceId", pipelineId);
        out.put("status", status);
        out.put("degraded", degraded);
        out.put("executionId", result.get("executionId"));
        out.put("executedNodes", result.get("executedNodes"));
        out.put("nodeTraces", result.get("nodeTraces"));
        out.put("outputs", result.get("outputs"));
        out.put("message", result.get("message"));
        out.put("definitionVersion", pointer[0]);
        out.put("definitionDigest", pointer[1]);
        log.info("编排分析已执行 analysis={} pipeline={} scenario={} status={} degraded={}",
                anchorId, pipelineId, scenarioId, status, degraded);
        return out;
    }

    private Map<String, Object> runForecast(String scenarioId, Map<String, Object> p, RunMode mode, String[] pointer) {
        String caliberId = require(p, "caliberId");
        String caliberVersion = require(p, "caliberVersion");
        String asOfTime = require(p, "asOfTime");
        String snapshotId = require(p, "snapshotId");
        String formulaVersion = require(p, "formulaVersion");
        String idempotencyKey = text(p.get("idempotencyKey"));
        if (idempotencyKey == null) {
            idempotencyKey = "idem-" + UUID.randomUUID().toString().substring(0, 18);
        }
        String contentHash = sha256(String.join("|", scenarioId, "FORECAST", caliberId, caliberVersion,
                asOfTime, snapshotId, formulaVersion, pointer[0] == null ? "" : pointer[0]));

        ForecastRunCommand command = new ForecastRunCommand(scenarioId, "FORECAST", caliberId, caliberVersion,
                asOfTime, snapshotId, formulaVersion, idempotencyKey, contentHash, mode,
                text(p.get("baselineRunId")));
        ForecastRunView view = forecastService.create(command);
        String anchorId = "run_" + UUID.randomUUID().toString().substring(0, 12);
        dao.insertAnalysisAnchor(anchorId, scenarioId, "FORECAST",
                view.getStatus() == null ? "RUNNING" : view.getStatus().name(),
                text(p.get("metric")), p.get("deviation"), view.isDegraded(), currentOperator(),
                mode.name(), text(p.get("baselineRunId")), pointer[0], pointer[1],
                "FORECAST_RUN", view.getForecastRunId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("analysisId", anchorId);
        out.put("scenarioId", scenarioId);
        out.put("analysisType", "FORECAST");
        out.put("runMode", mode.name());
        out.put("sourceType", "FORECAST_RUN");
        out.put("sourceId", view.getForecastRunId());
        out.put("status", view.getStatus() == null ? null : view.getStatus().name());
        out.put("degraded", view.isDegraded());
        out.put("definitionVersion", pointer[0]);
        out.put("definitionDigest", pointer[1]);
        log.info("预测分析已创建 analysis={} forecastRun={} scenario={}", anchorId, view.getForecastRunId(), scenarioId);
        return out;
    }

    private Map<String, Object> runScenarioRun(String scenarioId, Map<String, Object> p, RunMode mode,
                                               String[] pointer, String runType, String analysisType) {
        Map<String, Object> body = new LinkedHashMap<>(p);
        body.put("runTypes", List.of(runType));
        body.put("runMode", mode.name());
        ScenarioRunRequestValidator.RunCommand command = runValidator.validate(scenarioId, body);
        Map<String, Object> result = runService.run(scenarioId, body, command);
        String runId = String.valueOf(result.get("runId"));
        // 既有运行已写 ecos_scenario_run；补锚点列（不新增行，避免双计）
        dao.updateAnalysisAnchorMeta(runId, analysisType, "SCENARIO_RUN", runId, pointer[0], pointer[1]);
        Map<String, Object> out = new LinkedHashMap<>(result);
        out.put("analysisId", runId);
        out.put("analysisType", analysisType);
        out.put("sourceType", "SCENARIO_RUN");
        out.put("sourceId", runId);
        out.put("definitionVersion", pointer[0]);
        out.put("definitionDigest", pointer[1]);
        return out;
    }

    /** 比较：仅预测分析；来源必须是 FORECAST_RUN。 */
    public Map<String, Object> compare(String baselineAnalysisId, String candidateAnalysisId) {
        Map<String, Object> baseline = get(baselineAnalysisId);
        Map<String, Object> candidate = get(candidateAnalysisId);
        String bSource = str(baseline.get("source_type"));
        String cSource = str(candidate.get("source_type"));
        if (!"FORECAST_RUN".equalsIgnoreCase(bSource) || !"FORECAST_RUN".equalsIgnoreCase(cSource)) {
            throw new BusinessException(400, "ANALYSIS-400: 比较仅支持两条预测分析（sourceType=FORECAST_RUN）");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("baselineAnalysisId", baselineAnalysisId);
        out.put("candidateAnalysisId", candidateAnalysisId);
        out.put("baseline", baseline);
        out.put("candidate", candidate);
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("definitionVersion", !eq(baseline.get("definition_version"), candidate.get("definition_version")));
        diff.put("sourceRun", !eq(baseline.get("source_id"), candidate.get("source_id")));
        out.put("diff", diff);
        out.put("note", "差异标记仅表示元数据不同；预测数值请以两侧结果原文为准，不在后端合成 delta");
        return out;
    }

    private String[] definitionPointer(String scenarioId) {
        List<Map<String, Object>> rows = dao.findDefinitionPointer(scenarioId);
        if (rows.isEmpty()) {
            return new String[]{null, null};
        }
        Map<String, Object> row = rows.get(0);
        return new String[]{str(row.get("definition_version")), str(row.get("definition_digest"))};
    }

    private String require(Map<String, Object> p, String key) {
        String v = text(p.get(key));
        if (v == null) {
            throw new BusinessException(400, "ANALYSIS-400: 预测分析缺少必填项: " + key);
        }
        return v;
    }

    private static String upper(Object v) {
        String s = text(v);
        return s == null ? null : s.toUpperCase();
    }

    private static String text(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    /** 结构化结果 → JSON 文本（落 simulation_result 槽位；失败返 null，不伪造 "{}"）。 */
    private static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            log.warn("分析结果序列化失败，落 null: {}", e.getMessage());
            return null;
        }
    }

    private static String sha256(String v) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(v.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private String currentOperator() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String s) {
                return s;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "system";
    }
}
