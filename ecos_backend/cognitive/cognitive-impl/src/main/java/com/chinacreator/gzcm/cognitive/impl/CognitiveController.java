package com.chinacreator.gzcm.cognitive.impl;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.cognitive.ICognitiveEngineService;
import com.chinacreator.gzcm.cognitive.model.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 认知引擎 REST Controller — ECOS 认知层统一入口。
 *
 * <p>将三个推理器（RuleEngine、CausalReasoner、NsgaIIOptimizer）的能力
 * 通过 RESTful 端点暴露给前端和 Gateway。
 *
 * <h3>端点一览</h3>
 * <ol>
 *   <li>{@code GET  /api/v1/cognitive/blueprint} — 六层蓝图健康度</li>
 *   <li>{@code POST /api/v1/cognitive/reason}    — 规则推理 / 因果分析</li>
 *   <li>{@code POST /api/v1/cognitive/optimize}  — 帕累托多目标优化</li>
 *   <li>{@code POST /api/v1/cognitive/plan}      — 创建执行计划</li>
 *   <li>{@code GET  /api/v1/cognitive/plan/{id}} — 查询执行计划</li>
 * </ol>
 *
 * @author DataBridge Team
 */
@RestController
@RequestMapping("/api/v1/cognitive")
public class CognitiveController implements ICognitiveEngineService {

    private static final Logger log = LoggerFactory.getLogger(CognitiveController.class);

    // ── 推理器注入 ────────────────────────────────────────

    @Autowired
    private RuleEngine ruleEngine;

    @Autowired
    private CausalReasoner causalReasoner;

    @Autowired
    private NsgaIIOptimizer nsgaIIOptimizer;

    // ── 计划存储（stub: 内存 Map，后续对接 DB） ──────────

    private final Map<String, ExecutionPlan> planStore = new ConcurrentHashMap<>();

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(java.time.ZoneOffset.UTC);

    // ═══════════════════════════════════════════════════════
    // 端点 1: GET /api/v1/cognitive/blueprint
    // ═══════════════════════════════════════════════════════

    @Override
    @GetMapping("/blueprint")
    public ApiResponse<BlueprintResponse> getBlueprint(
            @RequestParam(required = false) String layer) {
        try {
            BlueprintResponse response = buildBlueprint(layer);
            log.info("GET /blueprint layer={} → overallScore={}", layer, response.getOverallScore());
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("蓝图查询失败: layer={}", layer, e);
            return ApiResponse.internalError("蓝图查询失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════
    // 端点 2: POST /api/v1/cognitive/reason
    // ═══════════════════════════════════════════════════════

    @Override
    @PostMapping("/reason")
    public ApiResponse<ReasonResponse> reason(@RequestBody ReasonRequest request) {
        try {
            // 参数校验
            String mode = request.getMode();
            if (mode == null || mode.isBlank()) {
                return ApiResponse.badRequest("请求参数校验失败: mode 不能为空");
            }

            Map<String, Object> facts = request.getFacts();
            if (facts == null || facts.isEmpty()) {
                return ApiResponse.badRequest("请求参数校验失败: facts 不能为空");
            }

            long startMs = System.currentTimeMillis();

            ReasonResponse response;
            switch (mode.toLowerCase()) {
                case "rule":
                    response = doRuleReason(facts, request.getContext(), request.getOptions());
                    break;
                case "causal":
                    response = doCausalReason(facts, request.getContext(), request.getOptions());
                    break;
                default:
                    return ApiResponse.badRequest(
                            "请求参数校验失败: mode 不在允许范围 [rule, causal]");
            }

            long elapsed = System.currentTimeMillis() - startMs;
            response.setElapsedMs(elapsed);

            log.info("POST /reason mode={} → elapsedMs={}", mode, elapsed);
            return ApiResponse.success(response);

        } catch (IllegalArgumentException e) {
            log.warn("推理参数校验失败: {}", e.getMessage());
            return ApiResponse.badRequest("请求参数校验失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("推理失败: mode={}", request.getMode(), e);
            return ApiResponse.internalError("推理失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════
    // 端点 3: POST /api/v1/cognitive/optimize
    // ═══════════════════════════════════════════════════════

    @Override
    @PostMapping("/optimize")
    public ApiResponse<OptimizeResponse> optimize(@RequestBody OptimizeRequest request) {
        try {
            OptimizationProblem problem = request.getProblem();
            if (problem == null) {
                return ApiResponse.badRequest("请求参数校验失败: problem 不能为空");
            }
            if (problem.getVariables() == null || problem.getVariables().isEmpty()) {
                return ApiResponse.badRequest("请求参数校验失败: variables 不能为空");
            }
            if (problem.getObjectives() == null || problem.getObjectives().isEmpty()) {
                return ApiResponse.badRequest("请求参数校验失败: objectives 不能为空");
            }

            // 构建约束检查器（默认全部可行）
            NsgaIIOptimizer.ConstraintChecker constraintChecker = vars -> true;

            // 构建目标函数评估器（模拟评估：基于变量值生成目标值）
            NsgaIIOptimizer.ObjectiveEvaluator evaluator = buildSimulatedEvaluator(problem);

            NsgaIIOptimizer.ParetoFrontResult result =
                    nsgaIIOptimizer.optimize(request, constraintChecker, evaluator);

            OptimizeResponse response = buildOptimizeResponse(result, problem, request.getOptions());

            log.info("POST /optimize → frontSize={}, generations={}, elapsedMs={}",
                    response.getParetoFront() != null ? response.getParetoFront().size() : 0,
                    response.getGenerations(), response.getElapsedMs());
            return ApiResponse.success(response);

        } catch (IllegalArgumentException e) {
            log.warn("优化参数校验失败: {}", e.getMessage());
            return ApiResponse.badRequest("请求参数校验失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("优化失败", e);
            return ApiResponse.internalError("优化失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════
    // 端点 4: POST /api/v1/cognitive/plan
    // ═══════════════════════════════════════════════════════

    @Override
    @PostMapping("/plan")
    public ApiResponse<ExecutionPlan> createPlan(@RequestBody CreatePlanRequest request) {
        try {
            if (request == null || request.getName() == null || request.getName().isBlank()) {
                return ApiResponse.badRequest("请求参数校验失败: name 不能为空");
            }

            ExecutionPlan plan = buildStubPlan(request);
            planStore.put(plan.getPlanId(), plan);

            log.info("POST /plan → planId={}, name={}", plan.getPlanId(), plan.getName());
            return ApiResponse.success(plan);

        } catch (Exception e) {
            log.error("创建计划失败", e);
            return ApiResponse.internalError("创建计划失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════
    // 端点 5: GET /api/v1/cognitive/plan/{id}
    // ═══════════════════════════════════════════════════════

    @Override
    @GetMapping("/plan/{id}")
    public ApiResponse<ExecutionPlan> getPlan(@PathVariable("id") String planId) {
        try {
            ExecutionPlan plan = planStore.get(planId);
            if (plan == null) {
                // 返回 stub 数据，避免硬 404
                plan = buildFallbackPlan(planId);
                log.debug("GET /plan/{} → stub fallback", planId);
            } else {
                log.debug("GET /plan/{} → found", planId);
            }
            return ApiResponse.success(plan);

        } catch (Exception e) {
            log.error("查询计划失败: id={}", planId, e);
            return ApiResponse.internalError("查询计划失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════
    // 可选: 健康检查（接口契约定义，未在 5 端点范围内但保留实现）
    // ═══════════════════════════════════════════════════════

    @Override
    @GetMapping("/health")
    public ApiResponse<HealthResponse> health() {
        HealthResponse hr = new HealthResponse();
        hr.setService("cognitive");
        hr.setStatus("UP");
        hr.setVersion("1.0.0");

        HealthResponse.ReasonerStatus reasoners = new HealthResponse.ReasonerStatus();

        HealthResponse.ReasonerDetail ruleDetail = new HealthResponse.ReasonerDetail();
        ruleDetail.setStatus(ruleEngine != null ? "UP" : "DOWN");
        ruleDetail.setRulesLoaded(ruleEngine != null ? ruleEngine.ruleCount() : 0);
        reasoners.setRuleEngine(ruleDetail);

        HealthResponse.ReasonerDetail causalDetail = new HealthResponse.ReasonerDetail();
        causalDetail.setStatus(causalReasoner != null ? "UP" : "DOWN");
        reasoners.setCausalReasoner(causalDetail);

        HealthResponse.ReasonerDetail nsgaDetail = new HealthResponse.ReasonerDetail();
        nsgaDetail.setStatus(nsgaIIOptimizer != null ? "UP" : "DOWN");
        reasoners.setNsgaIIOptimizer(nsgaDetail);

        hr.setReasoners(reasoners);
        return ApiResponse.success(hr);
    }

    // ═══════════════════════════════════════════════════════
    // 内部构建方法
    // ═══════════════════════════════════════════════════════

    /**
     * 构建六层蓝图健康度数据，并调用 RuleEngine 提取建议。
     */
    private BlueprintResponse buildBlueprint(String layerFilter) {
        BlueprintResponse response = new BlueprintResponse();
        response.setGeneratedAt(ISO_FORMATTER.format(Instant.now()));

        // 六层蓝图定义
        String[][] layerDefs = {
            {"L1", "数据接入层"},
            {"L2", "数据存储层"},
            {"L3", "数据治理层"},
            {"L4", "数据分析层"},
            {"L5", "数据服务层"},
            {"L6", "数据应用层"},
        };

        double[][] scores = {
            {92.0, 88.0, 74.0, 91.0, 85.0, 95.0},  // 健康评分
        };

        List<BlueprintLayer> layers = new ArrayList<>();
        for (int i = 0; i < layerDefs.length; i++) {
            String lid = layerDefs[i][0];
            if (layerFilter != null && !layerFilter.isBlank() && !lid.equalsIgnoreCase(layerFilter.trim())) {
                continue;
            }

            BlueprintLayer bl = new BlueprintLayer();
            bl.setLayerId(lid);
            bl.setName(layerDefs[i][1]);
            bl.setScore(scores[0][i]);
            bl.setStatus(scores[0][i] >= 90 ? "HEALTHY" : scores[0][i] >= 75 ? "WARNING" : "CRITICAL");

            // 模拟指标
            Map<String, Object> metrics = new LinkedHashMap<>();
            switch (lid) {
                case "L1":
                    metrics.put("activeStreams", 12);
                    metrics.put("throughput", "3500 rec/s");
                    metrics.put("latencyP99", "120ms");
                    metrics.put("errorRate", 0.02);
                    break;
                case "L2":
                    metrics.put("storageUsage", "68.5%");
                    metrics.put("connectionPool", "45/200");
                    metrics.put("queryLatencyP99", "85ms");
                    break;
                case "L3":
                    metrics.put("dqRulesActive", 156);
                    metrics.put("dqRulesFailing", 8);
                    metrics.put("lineageCoverage", "92%");
                    metrics.put("freshness", "15min");
                    break;
                case "L4":
                    metrics.put("activeQueries", 23);
                    metrics.put("avgResponseTime", "320ms");
                    metrics.put("cacheHitRate", 0.78);
                    break;
                case "L5":
                    metrics.put("apiQPS", 420);
                    metrics.put("apiErrorRate", 0.01);
                    metrics.put("activeConsumers", 18);
                    break;
                case "L6":
                    metrics.put("activeDashboards", 8);
                    metrics.put("activeReports", 35);
                    metrics.put("userSessions", 128);
                    break;
            }
            bl.setMetrics(metrics);

            // 告警
            List<Alert> alerts = new ArrayList<>();
            if ("L3".equals(lid)) {
                Alert a = new Alert();
                a.setSeverity("WARNING");
                a.setMessage("8 条 DQ 规则未通过，涉及表: t_order, t_payment");
                a.setRaisedAt(ISO_FORMATTER.format(Instant.now().minusSeconds(300)));
                alerts.add(a);
            }
            bl.setAlerts(alerts);

            layers.add(bl);
        }
        response.setLayers(layers);

        // 调用 RuleEngine：基于各层指标生成事实并推理
        List<Recommendation> recommendations = new ArrayList<>();
        try {
            Map<String, Object> facts = buildBlueprintFacts(layers);
            List<MatchedRule> matched = ruleEngine.evaluate(facts);
            if (matched != null && !matched.isEmpty()) {
                for (MatchedRule mr : matched) {
                    Recommendation rec = new Recommendation();
                    rec.setLayerId(mr.getRuleId() != null && mr.getRuleId().startsWith("L") ? mr.getRuleId() : "L3");
                    rec.setType("RULE_ACTION");
                    rec.setPriority(mr.getConfidence() != null && mr.getConfidence() >= 0.9 ? "HIGH" : "MEDIUM");
                    rec.setMessage((mr.getDescription() != null ? mr.getDescription() : mr.getRuleName()));
                    recommendations.add(rec);
                }
            }
        } catch (Exception e) {
            log.warn("RuleEngine 评估蓝图规则时异常（可能无规则加载）: {}", e.getMessage());
            // 添加默认建议
            Recommendation rec = new Recommendation();
            rec.setLayerId("L3");
            rec.setType("DQ_FIX");
            rec.setPriority("HIGH");
            rec.setMessage("建议排查并修复 t_order / t_payment 的 8 条失败 DQ 规则");
            recommendations.add(rec);
        }
        response.setRecommendations(recommendations);

        // 全局评分 = 各层均分
        double sum = layers.stream().mapToDouble(BlueprintLayer::getScore).sum();
        response.setOverallScore(Math.round(sum / Math.max(layers.size(), 1) * 10.0) / 10.0);

        return response;
    }

    /**
     * 从蓝图各层构建事实 Map，供 RuleEngine 评估。
     */
    private Map<String, Object> buildBlueprintFacts(List<BlueprintLayer> layers) {
        Map<String, Object> facts = new LinkedHashMap<>();
        for (BlueprintLayer bl : layers) {
            Map<String, Object> layerFact = new LinkedHashMap<>();
            layerFact.put("score", bl.getScore());
            layerFact.put("status", bl.getStatus());
            if (bl.getMetrics() != null) {
                layerFact.putAll(bl.getMetrics());
            }
            facts.put(bl.getLayerId(), layerFact);
        }
        return facts;
    }

    // ── 规则推理 ──────────────────────────────────────────

    /**
     * 执行规则推理：调用 RuleEngine.evaluate(facts)，构建 ReasonResponse。
     */
    private ReasonResponse doRuleReason(Map<String, Object> facts,
                                         Map<String, Object> context,
                                         Map<String, Object> options) {
        ReasonResponse response = new ReasonResponse();
        response.setMode("rule");

        long startNs = System.nanoTime();
        List<MatchedRule> matched = ruleEngine.evaluate(facts);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        response.setMatchedRules(matched);

        // 构建推理路径描述
        List<String> reasoningPath = new ArrayList<>();
        if (matched != null) {
            for (MatchedRule mr : matched) {
                reasoningPath.add("事实 → " + mr.getRuleName() + " 命中 (confidence="
                        + String.format("%.2f", mr.getConfidence() != null ? mr.getConfidence() : 0) + ")");
            }
        }
        if (reasoningPath.isEmpty()) {
            reasoningPath.add("无规则命中");
        }
        response.setReasoningPath(reasoningPath);
        response.setElapsedMs(elapsedMs);

        return response;
    }

    // ── 因果推理 ──────────────────────────────────────────

    /**
     * 执行因果推理：从 facts 提取事件名，构建因果图，调用 CausalReasoner 追溯根因。
     */
    @SuppressWarnings("unchecked")
    private ReasonResponse doCausalReason(Map<String, Object> facts,
                                           Map<String, Object> context,
                                           Map<String, Object> options) {
        ReasonResponse response = new ReasonResponse();
        response.setMode("causal");

        // 提取事件名
        String event = (String) facts.get("event");
        if (event == null) {
            event = "UNKNOWN_EVENT";
        }

        // 解析选项
        int maxDepth = parseOptionInt(options, "maxDepth", CausalReasoner.DEFAULT_MAX_DEPTH);
        int maxPaths = parseOptionInt(options, "maxPaths", 10);
        double confidenceThreshold = parseOptionDouble(options, "confidenceThreshold",
                CausalReasoner.DEFAULT_CONFIDENCE_THRESHOLD);

        // 构建因果图（从 context 或内建默认图）
        Map<String, List<CausalReasoner.CausalEdge>> graph;
        if (context != null && context.get("causalGraph") instanceof Map) {
            graph = parseCausalGraph((Map<String, Object>) context.get("causalGraph"));
        } else {
            graph = buildDefaultCausalGraph(event, facts);
        }

        // 追溯因果路径
        long startNs = System.nanoTime();
        List<CausalPath> allPaths = causalReasoner.traceAllPaths(event, graph, maxDepth, confidenceThreshold);
        List<RootCause> rootCauses = causalReasoner.findRootCauses(event, graph, confidenceThreshold);
        int kgNodes = causalReasoner.countReachableNodes(event, graph, confidenceThreshold);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        // 限制路径数量
        if (allPaths.size() > maxPaths) {
            allPaths = allPaths.stream()
                    .sorted(Comparator.comparing(CausalPath::getTotalConfidence,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(maxPaths)
                    .collect(Collectors.toList());
        }

        response.setCausalPaths(allPaths);
        response.setRootCauses(rootCauses);
        response.setKgNodesVisited(kgNodes);
        response.setElapsedMs(elapsedMs);

        return response;
    }

    /**
     * 构建默认因果图——从 facts 自动推断边关系。
     */
    private Map<String, List<CausalReasoner.CausalEdge>> buildDefaultCausalGraph(
            String event, Map<String, Object> facts) {
        Map<String, List<CausalReasoner.CausalEdge>> graph = new LinkedHashMap<>();

        // 基础因果边：典型分层依赖
        // L1 事件 → L2 可能原因 → L3 根因
        List<CausalReasoner.CausalEdge> fromEvent = new ArrayList<>();
        fromEvent.add(new CausalReasoner.CausalEdge(event, "KG_NODE_DB_CONN_01",
                "Doris FE 连接池耗尽", 0.91));
        fromEvent.add(new CausalReasoner.CausalEdge(event, "KG_NODE_KAFKA_LAG",
                "Kafka 消费 Lag 增大", 0.72));
        graph.put(event, fromEvent);

        List<CausalReasoner.CausalEdge> fromDb = new ArrayList<>();
        fromDb.add(new CausalReasoner.CausalEdge("KG_NODE_DB_CONN_01", "KG_NODE_DQ_TIMEOUT",
                "DQ 规则执行超时", 0.85));
        graph.put("KG_NODE_DB_CONN_01", fromDb);

        List<CausalReasoner.CausalEdge> fromDq = new ArrayList<>();
        fromDq.add(new CausalReasoner.CausalEdge("KG_NODE_DQ_TIMEOUT", "KG_NODE_LATENCY_SPIKE",
                "数据接入延迟飙升", 0.78));
        graph.put("KG_NODE_DQ_TIMEOUT", fromDq);

        graph.put("KG_NODE_KAFKA_LAG", Collections.emptyList());
        graph.put("KG_NODE_LATENCY_SPIKE", Collections.emptyList());

        return graph;
    }

    /**
     * 从 Map 解析因果图。
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<CausalReasoner.CausalEdge>> parseCausalGraph(
            Map<String, Object> graphMap) {
        Map<String, List<CausalReasoner.CausalEdge>> graph = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : graphMap.entrySet()) {
            List<CausalReasoner.CausalEdge> edges = new ArrayList<>();
            if (entry.getValue() instanceof List) {
                for (Object edgeObj : (List<Object>) entry.getValue()) {
                    if (edgeObj instanceof Map) {
                        Map<String, Object> em = (Map<String, Object>) edgeObj;
                        CausalReasoner.CausalEdge edge = new CausalReasoner.CausalEdge();
                        edge.setFrom((String) em.get("from"));
                        edge.setTo((String) em.get("to"));
                        edge.setLabel((String) em.get("label"));
                        Object conf = em.get("confidence");
                        if (conf instanceof Number) {
                            edge.setConfidence(((Number) conf).doubleValue());
                        }
                        edges.add(edge);
                    }
                }
            }
            graph.put(entry.getKey(), edges);
        }
        return graph;
    }

    // ── 优化 ──────────────────────────────────────────────

    /**
     * 构建模拟目标函数评估器。
     *
     * <p>使用简单的线性/非线性函数模拟真实目标函数评估。
     * 后续可替换为基于真实数据管道指标的评估器。
     */
    private NsgaIIOptimizer.ObjectiveEvaluator buildSimulatedEvaluator(OptimizationProblem problem) {
        List<Variable> variables = problem.getVariables();
        List<Objective> objectives = problem.getObjectives();
        int numVars = variables.size();
        int numObjs = objectives.size();

        final Random rng = new Random(42); // 固定种子便于复现

        return vars -> {
            double[] objValues = new double[numObjs];
            for (int i = 0; i < numObjs; i++) {
                Objective obj = objectives.get(i);
                // 基于变量值计算目标函数
                double val = 0;
                for (int j = 0; j < numVars; j++) {
                    Variable v = variables.get(j);
                    double normVal = normalizeVarValue(vars[j], v);

                    if (obj.getName() != null) {
                        String oName = obj.getName().toLowerCase();
                        // 根据目标名称给予不同权重，模拟现实语义
                        if (oName.contains("throughput") || oName.contains("吞吐")) {
                            val += normVal * (1000.0 + rng.nextDouble() * 200);
                        } else if (oName.contains("cost") || oName.contains("成本")) {
                            val += normVal * (2.0 + rng.nextDouble() * 10);
                        } else if (oName.contains("latency") || oName.contains("延迟")) {
                            val += (1.0 - normVal) * 200 + rng.nextDouble() * 20;
                        } else {
                            val += normVal * 100.0 + rng.nextDouble() * 50;
                        }
                    } else {
                        val += normVal * 100.0 + rng.nextDouble() * 50;
                    }

                    // 交叉项：变量间交互效应
                    if (j > 0) {
                        val += 0.1 * vars[j] * vars[j - 1] / 100.0;
                    }
                }
                objValues[i] = Math.max(0.0, val);
            }
            return objValues;
        };
    }

    private double normalizeVarValue(double varVal, Variable varDef) {
        double min = toDouble(varDef.getMin());
        double max = toDouble(varDef.getMax());
        if (max <= min) return 0.5;
        return Math.max(0.0, Math.min(1.0, (varVal - min) / (max - min)));
    }

    private double toDouble(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj instanceof String) {
            try { return Double.parseDouble((String) obj); }
            catch (NumberFormatException ignored) { /* fall through */ }
        }
        return 0.0;
    }

    /**
     * 将 NsgaIIOptimizer.ParetoFrontResult 映射为 OptimizeResponse。
     */
    private OptimizeResponse buildOptimizeResponse(
            NsgaIIOptimizer.ParetoFrontResult result,
            OptimizationProblem problem,
            Map<String, Object> options) {

        OptimizeResponse response = new OptimizeResponse();
        response.setFrontId("PF-" + Instant.now().toString().replace(":", "").replace("-", "").substring(0, 15));
        response.setGenerations(result.getGenerations());
        response.setElapsedMs(result.getElapsedMs());
        response.setConvergedAt(result.getConvergedAt() > 0 ? result.getConvergedAt() : null);

        // 映射帕累托解
        List<ParetoSolution> solutions = result.getSolutions();
        int idx = 0;
        for (ParetoSolution sol : solutions) {
            sol.setSolutionId("SOL-" + String.format("%03d", ++idx));
            if (sol.getFeasibility() == null) {
                sol.setFeasibility("FEASIBLE");
            }
        }
        response.setParetoFront(solutions);

        // 问题摘要
        ProblemSummary summary = new ProblemSummary();
        summary.setVariableCount(problem.getVariables() != null ? problem.getVariables().size() : 0);
        summary.setConstraintCount(problem.getConstraints() != null ? problem.getConstraints().size() : 0);
        summary.setObjectiveCount(problem.getObjectives() != null ? problem.getObjectives().size() : 0);
        summary.setFeasibleSolutions(solutions.size());
        summary.setInfeasibleSolutions(0);
        response.setProblemSummary(summary);

        return response;
    }

    // ── 计划 Stub ─────────────────────────────────────────

    /**
     * 构建 stub 执行计划。
     */
    private ExecutionPlan buildStubPlan(CreatePlanRequest request) {
        ExecutionPlan plan = new ExecutionPlan();
        plan.setPlanId("PLAN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        plan.setName(request.getName());
        plan.setPriority(request.getPriority() != null ? request.getPriority() : "P2");
        plan.setStatus("PENDING");
        plan.setSource(request.getSource());

        // 构建默认步骤
        List<PlanStep> steps = new ArrayList<>();
        PlanStep step1 = new PlanStep();
        step1.setStepId("STEP-001");
        step1.setOrder(1);
        step1.setAction("DIAGNOSE");
        step1.setTarget(request.getTarget() != null ? request.getTarget().getWorkflow() : "default-workflow");
        step1.setStatus("PENDING");
        step1.setParams(new LinkedHashMap<>());
        steps.add(step1);

        PlanStep step2 = new PlanStep();
        step2.setStepId("STEP-002");
        step2.setOrder(2);
        step2.setAction("EXECUTE");
        step2.setTarget(request.getTarget() != null ? request.getTarget().getWorkflow() : "default-workflow");
        step2.setStatus("PENDING");
        step2.setDependsOn(Collections.singletonList("STEP-001"));
        step2.setParams(new LinkedHashMap<>());
        steps.add(step2);

        plan.setSteps(steps);

        // 进度
        PlanProgress progress = new PlanProgress();
        progress.setTotalSteps(steps.size());
        progress.setCompletedSteps(0);
        progress.setPercentage(0);
        plan.setProgress(progress);

        plan.setEstimatedDurationMs(60_000L);
        plan.setCreatedAt(ISO_FORMATTER.format(Instant.now()));
        plan.setUpdatedAt(plan.getCreatedAt());

        return plan;
    }

    /**
     * 查询不存在时返回 fallback 计划（避免硬 404）。
     */
    private ExecutionPlan buildFallbackPlan(String planId) {
        ExecutionPlan plan = new ExecutionPlan();
        plan.setPlanId(planId);
        plan.setName("未知计划");
        plan.setStatus("UNKNOWN");
        plan.setPriority("P3");
        plan.setSteps(Collections.emptyList());
        PlanProgress progress = new PlanProgress();
        progress.setTotalSteps(0);
        progress.setCompletedSteps(0);
        progress.setPercentage(0);
        plan.setProgress(progress);
        plan.setCreatedAt(ISO_FORMATTER.format(Instant.now()));
        return plan;
    }

    // ── 选项解析工具 ──────────────────────────────────────

    private int parseOptionInt(Map<String, Object> options, String key, int defaultValue) {
        if (options == null) return defaultValue;
        Object val = options.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); }
            catch (NumberFormatException ignored) { /* fall through */ }
        }
        return defaultValue;
    }

    private double parseOptionDouble(Map<String, Object> options, String key, double defaultValue) {
        if (options == null) return defaultValue;
        Object val = options.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble((String) val); }
            catch (NumberFormatException ignored) { /* fall through */ }
        }
        return defaultValue;
    }
}
