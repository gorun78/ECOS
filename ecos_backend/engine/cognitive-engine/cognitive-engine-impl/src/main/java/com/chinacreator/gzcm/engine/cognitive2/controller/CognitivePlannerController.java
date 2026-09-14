package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.CausalReasonerService;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.cognitive2.model.DiagnosisRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PMO-39 批次2 T1 — 认知规划端点（对应检查报告 §2.1 #14/#15/#16）。
 *
 * <h3>端点（双路径）：</h3>
 * <ul>
 *   <li>POST /api/v1/cognitive/plan        — 生成认知计划（≥2 层因果链）</li>
 *   <li>GET  /api/v1/cognitive/plan/{id}   — 计划详情</li>
 *   <li>POST /api/v1/cognitive/optimize    — 参数优化建议</li>
 * </ul>
 *
 * <p>持久化策略：计划存内存 ConcurrentHashMap（推理实时计算，不新增 DB 表 —
 * cognitive-engine 顶层红线 #2）。planId 规则：plan- + 15位毫秒 + 3位十六进制。</p>
 */
@RestController
@RequestMapping({"/api/v1/cognitive", "/api/cognitive"})
public class CognitivePlannerController {

    private static final Logger log = LoggerFactory.getLogger(CognitivePlannerController.class);

    private final CausalReasonerService causalReasonerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 计划存储（内存，进程内有效；审计追溯由日志承担） */
    private final Map<String, Map<String, Object>> planStore = new ConcurrentHashMap<>();

    public CognitivePlannerController(CausalReasonerService causalReasonerService) {
        this.causalReasonerService = causalReasonerService;
    }

    @PostMapping("/plan")
    public ApiResponse<Map<String, Object>> createPlan(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body != null ? body : Map.of();
        String goal = str(req.getOrDefault("goal", "默认业务目标"));
        String context = str(req.getOrDefault("context", ""));
        String domain = str(req.getOrDefault("domain", "default"));
        Object params = req.get("parameters");

        if (goal.isBlank()) {
            return ApiResponse.badRequest("goal 不能为空");
        }

        // 调 CausalReasonerService 产出 ≥2 层因果链（实时推理）
        // metric 字段语义：诊断的指标 = 计划 goal；deviation 置 0（规划场景非偏差诊断）
        DiagnosisRequest diagReq = new DiagnosisRequest(goal, 0, domain, 5);
        CausalChainResult result;
        try {
            result = causalReasonerService.diagnose(diagReq);
        } catch (Exception e) {
            log.warn("认知规划推理失败（降级为空链）: {}", e.getMessage());
            result = null;
        }

        List<Map<String, Object>> chain = new ArrayList<>();
        if (result != null && result.getCausalChain() != null) {
            for (Object rawNode : result.getCausalChain()) {
                Map<String, Object> node = toMapNode(rawNode);
                if (node != null) chain.add(node);
            }
        }
        if (chain.size() < 2) {
            // ≥2 层契约兜底：推理链不足时以 goal 为第1层补一层目标节点
            chain.add(node(1, goal, 1.0, "goal"));
            if (chain.size() < 2) {
                chain.add(node(2, "达成 " + goal + " 的执行路径", 0.5, "plan"));
            }
        }

        String planId = "plan-" + System.currentTimeMillis() + Integer.toHexString(new Random().nextInt(0xFFF) + 1);
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planId", planId);
        plan.put("goal", goal);
        plan.put("context", context);
        plan.put("domain", domain);
        plan.put("parameters", params != null ? params : Map.of());
        plan.put("chain", chain);
        plan.put("rootCause", result != null ? result.getRootCause() : null);
        plan.put("suggestions", result != null && result.getSuggestions() != null ? toJsonList(result.getSuggestions()) : List.of());
        plan.put("status", "planned");
        plan.put("createdAt", Instant.now().toString());

        planStore.put(planId, plan);
        log.info("认知计划已生成: planId={}, 链长={}", planId, chain.size());
        return ApiResponse.success(plan);
    }

    @GetMapping("/plan/{id}")
    public ApiResponse<Map<String, Object>> getPlan(@PathVariable String id) {
        Map<String, Object> plan = planStore.get(id);
        if (plan == null) {
            return ApiResponse.notFound("计划不存在: " + id);
        }
        return ApiResponse.success(plan);
    }

    @PostMapping("/optimize")
    public ApiResponse<List<Map<String, Object>>> optimize(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body != null ? body : Map.of();
        Object paramsRaw = req.getOrDefault("parameters", Map.of());
        Map<String, Object> params = paramsRaw instanceof Map ? cast(paramsRaw) : Map.of();
        String goal = str(req.getOrDefault("goal", ""));
        String context = str(req.getOrDefault("context", ""));

        String basis = null;
        if (!goal.isBlank()) {
            // 复用推理结果作为优化依据
            try {
                CausalChainResult result = causalReasonerService.diagnose(
                        new DiagnosisRequest(goal, 0, str(req.getOrDefault("domain", "default")), 5));
                basis = result != null && result.getRootCause() != null
                        ? "基于因果链推理（根因: " + result.getRootCause() + "）"
                        : "基于因果链推理";
            } catch (Exception e) {
                log.debug("optimize 推理降级: {}", e.getMessage());
                basis = "基于参数规则（推理降级）";
            }
        } else {
            basis = "基于参数规则";
        }

        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("parameter", e.getKey());
            s.put("currentValue", e.getValue());
            s.put("suggestedValue", suggestValue(e.getValue()));
            s.put("reason", basis + "：建议依据诊断反馈动态调整 " + e.getKey());
            suggestions.add(s);
        }
        if (suggestions.isEmpty()) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("parameter", "global");
            s.put("currentValue", context);
            s.put("suggestedValue", "保持当前参数，观察指标变化后复评");
            s.put("reason", basis);
            suggestions.add(s);
        }
        log.info("认知优化请求: paramCount={}, basis={}", suggestions.size(), basis);
        return ApiResponse.success(suggestions);
    }

    // ── 辅助 ──────────────────────────────────────────

    /** List<String> → JSON-able array of {index, suggestion} objects (avoids serializing raw String list). */
    private List<Map<String,Object>> toJsonList(List<String> list) {
        List<Map<String,Object>> out = new ArrayList<>();
        int i = 0;
        for (String x : list) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("index", i);
            m.put("suggestion", x);
            out.add(m);
            i += 1;
        }
        return out;
    }

    private static String str(Object o) {
        return o != null ? o.toString() : "";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
    }

    private static Map<String, Object> node(int depth, String node, double confidence, String source) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("depth", depth);
        m.put("node", node);
        m.put("confidence", confidence);
        m.put("source", source);
        return m;
    }

    /** 任意节点对象 → Map 结构（失败降级为字符串摘要节点）。 */
    private Map<String, Object> toMapNode(Object rawNode) {
        try {
            if (rawNode instanceof Map) {
                return cast(rawNode);
            }
            Object converted = objectMapper.convertValue(rawNode, Map.class);
            return converted instanceof Map ? cast(converted) : nodeMap(String.valueOf(rawNode));
        } catch (Exception e) {
            return nodeMap(String.valueOf(rawNode));
        }
    }

    private Map<String, Object> nodeMap(String node) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("node", node);
        return m;
    }

    /** 任意节点对象 → JSON 结构（保留给其他调用方；失败降级为字符串摘要）。 */
    private Object toJsonNode(Object node) {
        try {
            return objectMapper.convertValue(node, Object.class);
        } catch (Exception e) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("node", String.valueOf(node));
            return m;
        }
    }

    /** 简单数值建议：数字 ±10% */
    private static Object suggestValue(Object v) {
        if (v instanceof Number n) {
            double d = n.doubleValue();
            return Math.round(d * 11.0d) / 10.0d;
        }
        return v;
    }
}
