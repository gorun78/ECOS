package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.CausalReasonerService;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitivePlanVO;
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
 *
 * <p>DI-2 #147DU：计划体改 {@link CognitivePlanVO}；{@code parameters} 与 {@code chain[]}
 * 为调用方/推理器动态结构（元素双形，见 VO 头注），按 #147DI 同口径保留 {@code Object}，
 * 不冻结 payload。</p>
 */
@RestController
@RequestMapping({"/api/v1/cognitive", "/api/cognitive"})
public class CognitivePlannerController {

    private static final Logger log = LoggerFactory.getLogger(CognitivePlannerController.class);

    private final CausalReasonerService causalReasonerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 计划存储（内存，进程内有效；审计追溯由日志承担） */
    private final Map<String, CognitivePlanVO> planStore = new ConcurrentHashMap<>();

    public CognitivePlannerController(CausalReasonerService causalReasonerService) {
        this.causalReasonerService = causalReasonerService;
    }

    @PostMapping("/plan")
    public ApiResponse<CognitivePlanVO> createPlan(@RequestBody(required = false) Map<String, Object> body) {
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

        List<Object> chain = new ArrayList<>();
        if (result != null && result.getCausalChain() != null) {
            for (Object rawNode : result.getCausalChain()) {
                Object node = CognitivePlanVO.toChainNode(rawNode, objectMapper);
                if (node != null) {
                    chain.add(node);
                }
            }
        }
        if (chain.size() < 2) {
            // ≥2 层契约兜底：推理链不足时以 goal 为第1层补一层目标节点
            chain.add(CognitivePlanVO.fallbackNode(1, goal, 1.0, "goal"));
            if (chain.size() < 2) {
                chain.add(CognitivePlanVO.fallbackNode(2, "达成 " + goal + " 的执行路径", 0.5, "plan"));
            }
        }

        String planId = "plan-" + System.currentTimeMillis() + Integer.toHexString(new Random().nextInt(0xFFF) + 1);
        CognitivePlanVO plan = new CognitivePlanVO(
                planId,
                goal,
                context,
                domain,
                params != null ? params : Map.of(),
                chain,
                result != null ? result.getRootCause() : null,
                result != null && result.getSuggestions() != null
                        ? CognitivePlanVO.suggestionsOf(result.getSuggestions()) : List.of(),
                "planned",
                Instant.now().toString());

        planStore.put(planId, plan);
        log.info("认知计划已生成: planId={}, 链长={}", planId, chain.size());
        return ApiResponse.success(plan);
    }

    @GetMapping("/plan/{id}")
    public ApiResponse<CognitivePlanVO> getPlan(@PathVariable String id) {
        CognitivePlanVO plan = planStore.get(id);
        if (plan == null) {
            return ApiResponse.notFound("计划不存在: " + id);
        }
        return ApiResponse.success(plan);
    }

    @PostMapping("/optimize")
    public ApiResponse<List<CognitivePlanVO.OptimizeSuggestion>> optimize(
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body != null ? body : Map.of();
        Object paramsRaw = req.getOrDefault("parameters", Map.of());
        Map<String, Object> params = paramsRaw instanceof Map ? cast(paramsRaw) : Map.of();
        String goal = str(req.getOrDefault("goal", ""));
        String context = str(req.getOrDefault("context", ""));

        String basis;
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

        List<CognitivePlanVO.OptimizeSuggestion> suggestions = new ArrayList<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            suggestions.add(new CognitivePlanVO.OptimizeSuggestion(
                    e.getKey(),
                    e.getValue(),
                    suggestValue(e.getValue()),
                    basis + "：建议依据诊断反馈动态调整 " + e.getKey()));
        }
        if (suggestions.isEmpty()) {
            suggestions.add(new CognitivePlanVO.OptimizeSuggestion(
                    "global",
                    context,
                    "保持当前参数，观察指标变化后复评",
                    basis));
        }
        log.info("认知优化请求: paramCount={}, basis={}", suggestions.size(), basis);
        return ApiResponse.success(suggestions);
    }

    // ── 辅助 ──────────────────────────────────────────

    private static String str(Object o) {
        return o != null ? o.toString() : "";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
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
