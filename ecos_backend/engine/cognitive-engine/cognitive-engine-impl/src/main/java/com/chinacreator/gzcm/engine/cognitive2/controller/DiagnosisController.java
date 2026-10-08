package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveContractSchema;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.CausalReasonerService;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.cognitive2.model.DiagnosisRequest;
import com.chinacreator.gzcm.engine.cognitive2.model.PreflightCheckResult;
import com.chinacreator.gzcm.engine.cognitive2.service.ModelRegistryService;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastDiagnosisService;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.access.graph.Neo4jClient;
import com.chinacreator.gzcm.runtime.llm.LLMGatewayService;
import com.chinacreator.gzcm.runtime.llm.model.ProfileConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 业务因果诊断（{@code /api/v1/cognitive/diagnose*}）。
 *
 * <p>F05-16 / X-44 诚实面：</p>
 * <ul>
 *   <li>{@code GET /diagnose/history} 原以进程内 {@code ConcurrentHashMap} 缓存最近 10 条诊断结果
 *       冒充持久化历史。认知域受 ADR-9 三档落盘口径约束（推理<b>结果</b>不落盘）且 V150~V199 无诊断
 *       历史表 ⇒ 本期不建表、不写 {@code cognitive_context*}，改为显式
 *       {@code 501 + errorCode=ECOS-COG-503 + data.stub=true}（映射保留，前端不 404，渲染"未实现"占位）。</li>
 *   <li>{@code POST /diagnose/preflight} 的 {@code neo4j_expected} / {@code llm_gateway_reachable}
 *       原硬编码 {@code true}，现改为真实探测：Neo4j 经 runtime-access {@code Neo4jClient#testConnection()}，
 *       LLM 经 {@code llm-gateway} profile 目录可达性。standard 档（{@code ecos.cognitive.neo4j-enabled=false}）
 *       Neo4j 项语义 = N/A（该档位不需要，{@code passed=true} + message 写明依据），不是探测假绿；
 *       探测异常一律 {@code passed=false}，不吞。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/cognitive")
public class DiagnosisController {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisController.class);

    /** llm-gateway profile 归属子系统标识（与 {@code SuggestionBuilder} 同源）。 */
    private static final String LLM_SUBSYSTEM = "cognitive";

    private final CausalReasonerService causalReasonerService;
    private final ModelRegistryService modelRegistryService;
    private final ObjectProvider<Neo4jClient> neo4jClientProvider;
    private final ObjectProvider<LLMGatewayService> llmGatewayProvider;
    /** F05-09 领域化诊断分支（forecastContext 存在时）；无 09 册接缝即 DG-9 fail-loud。 */
    private final ForecastDiagnosisService forecastDiagnosisService;

    /** Neo4j 是否启用（enterprise/ultimate 档为 true，standard 为 false） */
    private final boolean neo4jEnabled;

    public DiagnosisController(CausalReasonerService causalReasonerService,
                               ModelRegistryService modelRegistryService,
                               ObjectProvider<Neo4jClient> neo4jClientProvider,
                               ObjectProvider<LLMGatewayService> llmGatewayProvider,
                               ForecastDiagnosisService forecastDiagnosisService,
                               @Value("${ecos.cognitive.neo4j-enabled:false}") boolean neo4jEnabled) {
        this.causalReasonerService = causalReasonerService;
        this.modelRegistryService = modelRegistryService;
        this.neo4jClientProvider = neo4jClientProvider;
        this.llmGatewayProvider = llmGatewayProvider;
        this.forecastDiagnosisService = forecastDiagnosisService;
        this.neo4jEnabled = neo4jEnabled;
    }

    @PostMapping("/diagnose")
    public ApiResponse<?> diagnose(@RequestBody Map<String, Object> request) {
        String metric = (String) request.get("metric");
        double deviation = request.get("deviation") != null ?
            ((Number) request.get("deviation")).doubleValue() : 0;
        String domain = (String) request.getOrDefault("domain", "default");
        int maxDepth = request.get("maxDepth") != null ?
            ((Number) request.get("maxDepth")).intValue() : 5;

        log.info("因果诊断请求: metric={}, deviation={}, domain={}, maxDepth={}", metric, deviation, domain, maxDepth);

        // F05-09 只增不改：forecastContext 为可选新增块。存在即走 C.2 确定性快照分支
        // （数值只来自 ForecastRunSnapshotReader + 纯函数复算）；缺省时下方行为与响应 schema 一字不变。
        CognitiveContractSchema.ForecastContextVO forecastContext = parseForecastContext(request);
        CognitiveContractSchema.DiagnoseForecastData forecastData = forecastContext == null
                ? null : forecastDiagnosisService.diagnose(forecastContext);

        // 调用T1重写后的完整因果诊断方法
        DiagnosisRequest diagReq = new DiagnosisRequest(metric, deviation, domain, maxDepth);
        CausalChainResult result = causalReasonerService.diagnose(diagReq);

        // Wave-7.1 T-26B: 防御性 null-check — 若诊断流程因内部异常返回 null，降级为 500
        if (result == null) {
            log.error("因果诊断服务返回 null: metric={}", metric);
            return ApiResponse.internalError("诊断服务内部错误，请稍后重试");
        }

        // PMO-51 T4: 指标在 KG 中未覆盖 → **降级**而非 404：
        // 引擎已做规则兜底（弱数据态），仍返回因果链 + 低置信度标注
        // （forecastContext 分支已从预测运行快照取到确定性链路时不再走该 notFound）
        if (!result.isMetricFound() && result.getCausalChain().isEmpty() && forecastData == null) {
            return ApiResponse.notFound("指标 '" + metric + "' 在知识图谱中不存在，且规则兜底无覆盖，无法产出诊断");
        }
        boolean degraded = !result.isMetricFound();
        if (degraded) {
            log.info("指标 '{}' 已降级诊断（KG 未覆盖，规则兜底，低置信度）", metric);
        }

        // F05-16: 仅本次请求的诊断标识（推理结果不落盘，ADR-9；无进程内历史缓存）
        String diagnosisId = UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("diagnosisId", diagnosisId);
        response.put("degraded", degraded);
        if (degraded) {
            response.put("degradeReason", "KG 未覆盖该指标，已切换至规则兜底（低置信度，建议补充本体/KG 覆盖以提升可信度）");
        }
        response.put("rootCause", result.getRootCause());
        response.put("causalChain", result.getCausalChain());
        response.put("suggestions", result.getSuggestions());
        response.put("affectedMetrics", result.getAffectedMetrics());
        // Wave-3.2 增量：推理路径（含 steps / ruleRefs / precedentRefs / clauses）
        if (result.getReasoningPath() != null) {
            response.put("reasoningPath", result.getReasoningPath());
        }

        // F05-09 / D.3 响应增块：chains[]（确定性复算 + 证据/假设分离）+ LLM 解释（DG-8 可降级）
        if (forecastData != null) {
            response.put("chains", forecastData.getChains());
            if (forecastData.getExplanation() != null) {
                response.put("explanation", forecastData.getExplanation());
            }
            response.put("explanationUnavailable", forecastData.isExplanationUnavailable());
        }

        return ApiResponse.success(response);
    }

    /**
     * 解析可选 {@code forecastContext} 增块（D.3 六字段）。
     *
     * <p>缺块返回 {@code null} = 走既有通用诊断分支；有块但缺 {@code forecastRunId} 即 400
     * （无法锁定快照范围，要点4；禁退化为全库扫描或静默忽略）。</p>
     */
    @SuppressWarnings("unchecked")
    private static CognitiveContractSchema.ForecastContextVO parseForecastContext(
            Map<String, Object> request) {
        Object raw = request.get("forecastContext");
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Map)) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "forecastContext 必须为对象");
        }
        Map<String, Object> fc = (Map<String, Object>) raw;
        CognitiveContractSchema.ForecastContextVO vo = new CognitiveContractSchema.ForecastContextVO();
        vo.setForecastRunId(text(fc.get("forecastRunId")));
        vo.setCaliberId(text(fc.get("caliberId")));
        vo.setCaliberVersion(text(fc.get("caliberVersion")));
        vo.setAsOfTime(text(fc.get("asOfTime")));
        vo.setProjectId(text(fc.get("projectId")));
        vo.setDepartmentId(text(fc.get("departmentId")));
        if (vo.getForecastRunId() == null || vo.getForecastRunId().isBlank()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "forecastContext.forecastRunId 缺失");
        }
        return vo;
    }

    private static String text(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /**
     * GET /api/v1/cognitive/diagnose/history — 本期无持久化实现，显式 stub。
     *
     * <p>真实 501（HTTP 状态 + {@code code=501}）+ {@code data.stub=true}，供前端渲染"未实现"占位；
     * 禁 200 + 进程内缓存冒充历史（X-44）。路径与查询参数签名保持不变（API 只增不改）。</p>
     */
    @GetMapping("/diagnose/history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHistory() {
        return CognitiveStubResponses.notImplemented();
    }

    /**
     * 推理前 5 项预检 — V2 原型 §02 情境诊断 Step 5。
     *
     * <p>检查上下文完整性 / 模型可执行 / 权限 / Neo4j 预期 / LLM 网关可达性。
     * 全部硬项通过 → PASSED；软项（neo4j/llm）未通过 → DEGRADED；
     * 任一硬项（context/model/permission）未通过 → FAILED。</p>
     */
    @PostMapping("/diagnose/preflight")
    public ApiResponse<PreflightCheckResult> preflight(@RequestBody DiagnosisRequest request) {
        PreflightCheckResult result = new PreflightCheckResult();
        List<PreflightCheckResult.CheckItem> checks = new ArrayList<>();
        boolean hardFail = false;
        boolean softFail = false;

        // 1. context_loaded — 请求包含有效指标名且非空
        boolean contextOk = request.getMetric() != null && !request.getMetric().isBlank();
        PreflightCheckResult.CheckItem context = new PreflightCheckResult.CheckItem();
        context.setName("context_loaded");
        context.setPassed(contextOk);
        context.setMessage(contextOk
                ? "指标 '" + request.getMetric() + "' 上下文有效"
                : "请求缺少有效指标（metric 为空）");
        checks.add(context);
        if (!contextOk) {
            hardFail = true;
        }

        // 2. model_ready — 至少存在一个 active 的认知模型
        boolean modelOk = false;
        String modelMsg = "模型注册表查询异常";
        try {
            List<Map<String, Object>> models = modelRegistryService.listModels(null);
            long activeCount = models.stream()
                    .filter(m -> "active".equals(m.get("status")))
                    .count();
            modelOk = activeCount >= 1;
            modelMsg = modelOk
                    ? "存在 " + activeCount + " 个 active 模型，推理就绪"
                    : "注册表中无 active 模型（当前 active 数=" + activeCount + "）";
        } catch (Exception e) {
            log.error("预检 model_ready 查询失败: {}", e.getMessage(), e);
            modelMsg = "模型注册表查询异常: " + e.getClass().getSimpleName();
        }
        PreflightCheckResult.CheckItem model = new PreflightCheckResult.CheckItem();
        model.setName("model_ready");
        model.setPassed(modelOk);
        model.setMessage(modelMsg);
        checks.add(model);
        if (!modelOk) {
            hardFail = true;
        }

        // 3. user_access — 基础非空校验（domain 字段）
        boolean accessOk = request.getDomain() != null && !request.getDomain().isBlank();
        PreflightCheckResult.CheckItem access = new PreflightCheckResult.CheckItem();
        access.setName("user_access");
        access.setPassed(accessOk);
        access.setMessage(accessOk
                ? "业务域 '" + request.getDomain() + "' 有效"
                : "domain 字段缺失，无法确定业务域权限范围");
        checks.add(access);
        if (!accessOk) {
            hardFail = true;
        }

        // 4. neo4j_expected — 软项：真实连通性探测（standard 档 = N/A 通过，非假绿）
        boolean neo4jOk;
        String neo4jMsg;
        if (!neo4jEnabled) {
            neo4jOk = true;
            neo4jMsg = "N/A：ecos.cognitive.neo4j-enabled=false，当前档位 PG-only 不需要 Neo4j"
                    + "（依据 .trae/rules/数据库访问规范.md ST02：standard 档禁实启 Neo4j）";
        } else {
            try {
                Neo4jClient client = neo4jClientProvider.getIfAvailable();
                if (client == null) {
                    neo4jOk = false;
                    neo4jMsg = "enterprise/ultimate 档已启用 Neo4j，但 runtime-access Neo4jClient 未装配";
                } else {
                    neo4jOk = client.testConnection();
                    neo4jMsg = neo4jOk
                            ? "Neo4j 连通性探测通过（Neo4jClient.testConnection）"
                            : "Neo4j 连通性探测失败（Neo4jClient.testConnection=false）";
                }
            } catch (Exception e) {
                neo4jOk = false;
                neo4jMsg = "Neo4j 探测异常：" + e.getClass().getSimpleName();
                log.warn("预检 neo4j_expected 探测异常: {}", e.toString(), e);
            }
        }
        PreflightCheckResult.CheckItem neo4j = new PreflightCheckResult.CheckItem();
        neo4j.setName("neo4j_expected");
        neo4j.setPassed(neo4jOk);
        neo4j.setMessage(neo4jMsg);
        checks.add(neo4j);
        if (!neo4jOk) {
            softFail = true;
        }

        // 5. llm_gateway_reachable — 软项：经 llm-gateway 真实探测（LLM 仍为可选能力，非硬门禁）
        boolean llmOk;
        String llmMsg;
        try {
            LLMGatewayService gateway = llmGatewayProvider.getIfAvailable();
            if (gateway == null) {
                llmOk = false;
                llmMsg = "llm-gateway 未装配（LLMGatewayService bean 缺失），LLM 增强不可用";
            } else {
                List<ProfileConfig> profiles = gateway.listProfiles(LLM_SUBSYSTEM);
                llmOk = profiles != null && !profiles.isEmpty();
                llmMsg = llmOk
                        ? "llm-gateway 可达，" + profiles.size() + " 个 cognitive profile 就绪"
                        : "llm-gateway 已装配但 subsystem=cognitive 无可用 profile，LLM 增强不可用";
            }
        } catch (Exception e) {
            llmOk = false;
            llmMsg = "llm-gateway 探测异常：" + e.getClass().getSimpleName();
            log.warn("预检 llm_gateway_reachable 探测异常: {}", e.toString(), e);
        }
        PreflightCheckResult.CheckItem llm = new PreflightCheckResult.CheckItem();
        llm.setName("llm_gateway_reachable");
        llm.setPassed(llmOk);
        llm.setMessage(llmMsg);
        checks.add(llm);
        if (!llmOk) {
            softFail = true;
        }

        // 汇总状态
        if (hardFail) {
            result.setStatus("FAILED");
        } else if (softFail) {
            result.setStatus("DEGRADED");
        } else {
            result.setStatus("PASSED");
        }
        result.setChecks(checks);
        result.setTimestamp(System.currentTimeMillis());

        log.info("推理前预检完成: status={}, metric={}, activeChecks={}/5",
                result.getStatus(), request.getMetric(),
                checks.stream().filter(PreflightCheckResult.CheckItem::isPassed).count());

        return ApiResponse.success(result);
    }
}
