package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.CausalReasonerService;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.cognitive2.model.DiagnosisRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/cognitive")
public class DiagnosisController {
    private static final Logger log = LoggerFactory.getLogger(DiagnosisController.class);

    @Autowired
    private CausalReasonerService causalReasonerService;

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
}
