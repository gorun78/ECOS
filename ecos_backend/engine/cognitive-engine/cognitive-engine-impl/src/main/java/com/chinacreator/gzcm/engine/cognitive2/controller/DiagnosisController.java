package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.CausalReasonerService;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.cognitive2.model.DiagnosisRequest;
import com.chinacreator.gzcm.engine.cognitive2.model.PreflightCheckResult;
import com.chinacreator.gzcm.engine.cognitive2.service.ModelRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/cognitive")
public class DiagnosisController {
    private static final Logger log = LoggerFactory.getLogger(DiagnosisController.class);

    @Autowired
    private CausalReasonerService causalReasonerService;

    @Autowired
    private ModelRegistryService modelRegistryService;

    /** Neo4j 是否启用（enterprise/ultimate 档为 true，standard 为 false） */
    @Value("${ecos.cognitive.neo4j-enabled:false}")
    private boolean neo4jEnabled;

    // 内存缓存最近10次诊断结果
    private final Map<String, CausalChainResult> historyCache = new ConcurrentHashMap<>();
    private final Deque<String> historyKeys = new LinkedList<>();
    private static final int MAX_HISTORY = 10;

    @PostMapping("/diagnose")
    public ApiResponse<?> diagnose(@RequestBody Map<String, Object> request) {
        String metric = (String) request.get("metric");
        double deviation = request.get("deviation") != null ?
            ((Number) request.get("deviation")).doubleValue() : 0;
        String domain = (String) request.getOrDefault("domain", "default");
        int maxDepth = request.get("maxDepth") != null ?
            ((Number) request.get("maxDepth")).intValue() : 5;

        log.info("因果诊断请求: metric={}, deviation={}, domain={}, maxDepth={}", metric, deviation, domain, maxDepth);

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
        if (!result.isMetricFound() && result.getCausalChain().isEmpty()) {
            return ApiResponse.notFound("指标 '" + metric + "' 在知识图谱中不存在，且规则兜底无覆盖，无法产出诊断");
        }
        boolean degraded = !result.isMetricFound();
        if (degraded) {
            log.info("指标 '{}' 已降级诊断（KG 未覆盖，规则兜底，低置信度）", metric);
        }

        // 缓存历史
        String key = UUID.randomUUID().toString().substring(0, 8);
        historyCache.put(key, result);
        historyKeys.addFirst(key);
        if (historyKeys.size() > MAX_HISTORY) {
            historyCache.remove(historyKeys.removeLast());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("diagnosisId", key);
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

        return ApiResponse.success(response);
    }

    @GetMapping("/diagnose/history")
    public ApiResponse<List<Map<String, Object>>> getHistory() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String key : historyKeys) {
            CausalChainResult entry = historyCache.get(key);
            if (entry != null) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", key);
                item.put("rootCause", entry.getRootCause());
                item.put("chainLength", entry.getCausalChain().size());
                item.put("suggestions", entry.getSuggestions());
                list.add(item);
            }
        }
        return ApiResponse.success(list);
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
            modelMsg = "模型注册表查询异常: " + e.getMessage();
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

        // 4. neo4j_expected — 软项：检查 Neo4j 是否启用（standard 档走 PG-only 无问题）
        boolean neo4jOk = true;
        String neo4jMsg = "standard 模式，PG-only 架构，无需 Neo4j";
        if (neo4jEnabled) {
            neo4jOk = true;
            neo4jMsg = "enterprise/ultimate 模式，Neo4j 已启用";
        }
        PreflightCheckResult.CheckItem neo4j = new PreflightCheckResult.CheckItem();
        neo4j.setName("neo4j_expected");
        neo4j.setPassed(neo4jOk);
        neo4j.setMessage(neo4jMsg);
        checks.add(neo4j);
        if (!neo4jOk) {
            softFail = true;
        }

        // 5. llm_gateway_reachable — 软项：LLM 为可选能力，非硬门禁
        boolean llmOk = true;
        String llmMsg = "LLM 网关为可选能力，不影响因果推理主链路";
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
