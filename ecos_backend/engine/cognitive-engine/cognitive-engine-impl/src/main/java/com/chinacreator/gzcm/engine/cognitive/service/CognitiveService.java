package com.chinacreator.gzcm.engine.cognitive.service;

import com.chinacreator.gzcm.common.engine.IEngine;
import com.chinacreator.gzcm.common.service.IGraphService;
import com.chinacreator.gzcm.cognitive.impl.CausalReasoner;
import com.chinacreator.gzcm.cognitive.impl.NsgaIIOptimizer;
import com.chinacreator.gzcm.cognitive.impl.RuleEngine;
import com.chinacreator.gzcm.cognitive.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CognitiveService {

    private static final Logger log = LoggerFactory.getLogger(CognitiveService.class);
    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(java.time.ZoneOffset.UTC);

    private final RuleEngine ruleEngine;
    private final CausalReasoner causalReasoner;
    private final NsgaIIOptimizer nsgaIIOptimizer;
    private final JdbcTemplate jdbc;
    private final IGraphService graphService;
    private final List<IEngine> engines;

    public CognitiveService(RuleEngine ruleEngine,
                            CausalReasoner causalReasoner,
                            NsgaIIOptimizer nsgaIIOptimizer,
                            JdbcTemplate jdbc,
                            IGraphService graphService,
                            List<IEngine> engines) {
        this.ruleEngine = ruleEngine;
        this.causalReasoner = causalReasoner;
        this.nsgaIIOptimizer = nsgaIIOptimizer;
        this.jdbc = jdbc;
        this.graphService = graphService;
        this.engines = engines;
    }

    public BlueprintResponse buildBlueprintFromEngineStates(String layerFilter) {
        BlueprintResponse response = new BlueprintResponse();
        response.setGeneratedAt(ISO_FMT.format(Instant.now()));

        String[][] layerDefs = {
                {"L1", "数据接入层"}, {"L2", "数据存储层"}, {"L3", "数据治理层"},
                {"L4", "数据分析层"}, {"L5", "数据服务层"}, {"L6", "数据应用层"},
        };

        List<BlueprintLayer> layers = new ArrayList<>();
        for (String[] ld : layerDefs) {
            String lid = ld[0];
            if (layerFilter != null && !layerFilter.isBlank() && !lid.equalsIgnoreCase(layerFilter.trim())) continue;

            BlueprintLayer bl = new BlueprintLayer();
            bl.setLayerId(lid);
            bl.setName(ld[1]);
            double score = computeLayerScore(lid);
            bl.setScore(score);
            bl.setStatus(score >= 90 ? "HEALTHY" : score >= 75 ? "WARNING" : "CRITICAL");
            bl.setMetrics(collectLayerMetrics(lid));
            bl.setAlerts(collectLayerAlerts(lid));
            layers.add(bl);
        }
        response.setLayers(layers);

        List<Recommendation> recommendations = new ArrayList<>();
        try {
            Map<String, Object> facts = buildBlueprintFacts(layers);
            List<MatchedRule> matched = ruleEngine.evaluate(facts);
            if (matched != null) {
                for (MatchedRule mr : matched) {
                    Recommendation rec = new Recommendation();
                    rec.setLayerId(mr.getRuleId() != null && mr.getRuleId().startsWith("L") ? mr.getRuleId() : "L3");
                    rec.setType("RULE_ACTION");
                    rec.setPriority(mr.getConfidence() != null && mr.getConfidence() >= 0.9 ? "HIGH" : "MEDIUM");
                    rec.setMessage(mr.getDescription() != null ? mr.getDescription() : mr.getRuleName());
                    recommendations.add(rec);
                }
            }
        } catch (Exception e) {
            log.warn("RuleEngine evaluation failed: {}", e.getMessage());
        }
        response.setRecommendations(recommendations);

        double sum = layers.stream().mapToDouble(BlueprintLayer::getScore).sum();
        response.setOverallScore(Math.round(sum / Math.max(layers.size(), 1) * 10.0) / 10.0);
        return response;
    }

    private double computeLayerScore(String layerId) {
        try {
            switch (layerId) {
                case "L1":
                    Long dsCount = jdbc.queryForObject("SELECT COUNT(*) FROM td_datasource WHERE status='active'", Long.class);
                    return dsCount != null && dsCount > 0 ? 85 + Math.min(dsCount, 15) : 50.0;
                case "L2":
                    Long resCount = jdbc.queryForObject("SELECT COUNT(*) FROM td_data_resource", Long.class);
                    return resCount != null && resCount > 0 ? 80 + Math.min(resCount / 10, 20) : 50.0;
                case "L3":
                    Long dqRules = jdbc.queryForObject("SELECT COUNT(*) FROM ecos_quality_rule", Long.class);
                    return dqRules != null && dqRules > 0 ? 75 + Math.min(dqRules / 5, 25) : 50.0;
                case "L4":
                    Long pipelines = jdbc.queryForObject("SELECT COUNT(*) FROM ecos_pipeline", Long.class);
                    return pipelines != null && pipelines > 0 ? 80 + Math.min(pipelines / 2, 20) : 50.0;
                case "L5":
                    Long catalogs = jdbc.queryForObject("SELECT COUNT(*) FROM td_catalog_item", Long.class);
                    return catalogs != null && catalogs > 0 ? 82 + Math.min(catalogs / 5, 18) : 50.0;
                case "L6":
                    Long agents = jdbc.queryForObject("SELECT COUNT(*) FROM ecos_agent_registry", Long.class);
                    return agents != null && agents > 0 ? 85 + Math.min(agents, 15) : 50.0;
                default:
                    return 70.0;
            }
        } catch (Exception e) {
            log.debug("Score computation failed for {}: {}", layerId, e.getMessage());
            return 70.0;
        }
    }

    private Map<String, Object> collectLayerMetrics(String layerId) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        try {
            switch (layerId) {
                case "L1":
                    metrics.put("activeStreams", jdbc.queryForObject("SELECT COUNT(*) FROM td_datasource WHERE status='active'", Long.class));
                    break;
                case "L2":
                    metrics.put("storageResources", jdbc.queryForObject("SELECT COUNT(*) FROM td_data_resource", Long.class));
                    break;
                case "L3":
                    metrics.put("dqRulesActive", jdbc.queryForObject("SELECT COUNT(*) FROM ecos_quality_rule", Long.class));
                    break;
                case "L4":
                    metrics.put("activePipelines", jdbc.queryForObject("SELECT COUNT(*) FROM ecos_pipeline", Long.class));
                    break;
                case "L5":
                    metrics.put("catalogItems", jdbc.queryForObject("SELECT COUNT(*) FROM td_catalog_item", Long.class));
                    break;
                case "L6":
                    metrics.put("activeAgents", jdbc.queryForObject("SELECT COUNT(*) FROM ecos_agent_registry", Long.class));
                    break;
            }
        } catch (Exception e) {
            log.debug("Metrics collection failed for {}: {}", layerId, e.getMessage());
        }
        return metrics;
    }

    private List<Alert> collectLayerAlerts(String layerId) {
        List<Alert> alerts = new ArrayList<>();
        return alerts;
    }

    private Map<String, Object> buildBlueprintFacts(List<BlueprintLayer> layers) {
        Map<String, Object> facts = new LinkedHashMap<>();
        for (BlueprintLayer bl : layers) {
            Map<String, Object> layerFact = new LinkedHashMap<>();
            layerFact.put("score", bl.getScore());
            layerFact.put("status", bl.getStatus());
            if (bl.getMetrics() != null) layerFact.putAll(bl.getMetrics());
            facts.put(bl.getLayerId(), layerFact);
        }
        return facts;
    }

    public ReasonResponse doRuleReason(Map<String, Object> facts, Map<String, Object> context, Map<String, Object> options) {
        ReasonResponse response = new ReasonResponse();
        response.setMode("rule");
        long startNs = System.nanoTime();
        List<MatchedRule> matched = ruleEngine.evaluate(facts);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        response.setMatchedRules(matched);

        List<String> reasoningPath = new ArrayList<>();
        if (matched != null) {
            for (MatchedRule mr : matched) {
                reasoningPath.add("事实 → " + mr.getRuleName() + " 命中 (confidence="
                        + String.format("%.2f", mr.getConfidence() != null ? mr.getConfidence() : 0) + ")");
            }
        }
        if (reasoningPath.isEmpty()) reasoningPath.add("无规则命中");
        response.setReasoningPath(reasoningPath);
        response.setElapsedMs(elapsedMs);
        return response;
    }

    @SuppressWarnings("unchecked")
    public ReasonResponse doCausalReason(Map<String, Object> facts, Map<String, Object> context, Map<String, Object> options) {
        ReasonResponse response = new ReasonResponse();
        response.setMode("causal");

        String event = (String) facts.get("event");
        if (event == null) event = "UNKNOWN_EVENT";

        int maxDepth = parseOptionInt(options, "maxDepth", CausalReasoner.DEFAULT_MAX_DEPTH);
        int maxPaths = parseOptionInt(options, "maxPaths", 10);
        double confidenceThreshold = parseOptionDouble(options, "confidenceThreshold",
                CausalReasoner.DEFAULT_CONFIDENCE_THRESHOLD);

        Map<String, List<CausalReasoner.CausalEdge>> graph;
        if (context != null && context.get("causalGraph") instanceof Map) {
            graph = parseCausalGraph((Map<String, Object>) context.get("causalGraph"));
        } else {
            graph = loadCausalGraphFromKnowledgeBase(event);
            if (graph.isEmpty()) {
                graph = buildDefaultCausalGraph(event, facts);
            }
        }

        long startNs = System.nanoTime();
        List<CausalPath> allPaths = causalReasoner.traceAllPaths(event, graph, maxDepth, confidenceThreshold);
        List<RootCause> rootCauses = causalReasoner.findRootCauses(event, graph, confidenceThreshold);
        int kgNodes = causalReasoner.countReachableNodes(event, graph, confidenceThreshold);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

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

    private Map<String, List<CausalReasoner.CausalEdge>> loadCausalGraphFromKnowledgeBase(String event) {
        Map<String, List<CausalReasoner.CausalEdge>> graph = new LinkedHashMap<>();
        try {
            Map<String, Object> subgraph = graphService.getSubgraph(event);
            if (subgraph != null && subgraph.containsKey("edges")) {
                List<Map<String, Object>> edges = (List<Map<String, Object>>) subgraph.get("edges");
                for (Map<String, Object> edge : edges) {
                    String source = String.valueOf(edge.getOrDefault("source_id", edge.get("source")));
                    String target = String.valueOf(edge.getOrDefault("target_id", edge.get("target")));
                    String label = String.valueOf(edge.getOrDefault("rel_type", edge.getOrDefault("type", "")));
                    double confidence = edge.get("strength") instanceof Number
                            ? ((Number) edge.get("strength")).doubleValue() : 0.8;

                    graph.computeIfAbsent(source, k -> new ArrayList<>())
                            .add(new CausalReasoner.CausalEdge(source, target, label, confidence));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to load causal graph from knowledge base: {}", e.getMessage());
        }
        return graph;
    }

    private Map<String, List<CausalReasoner.CausalEdge>> buildDefaultCausalGraph(
            String event, Map<String, Object> facts) {
        Map<String, List<CausalReasoner.CausalEdge>> graph = new LinkedHashMap<>();
        List<CausalReasoner.CausalEdge> fromEvent = new ArrayList<>();
        fromEvent.add(new CausalReasoner.CausalEdge(event, "KG_NODE_DB_CONN_01", "Doris FE 连接池耗尽", 0.91));
        fromEvent.add(new CausalReasoner.CausalEdge(event, "KG_NODE_KAFKA_LAG", "Kafka 消费 Lag 增大", 0.72));
        graph.put(event, fromEvent);

        List<CausalReasoner.CausalEdge> fromDb = new ArrayList<>();
        fromDb.add(new CausalReasoner.CausalEdge("KG_NODE_DB_CONN_01", "KG_NODE_DQ_TIMEOUT", "DQ 规则执行超时", 0.85));
        graph.put("KG_NODE_DB_CONN_01", fromDb);

        List<CausalReasoner.CausalEdge> fromDq = new ArrayList<>();
        fromDq.add(new CausalReasoner.CausalEdge("KG_NODE_DQ_TIMEOUT", "KG_NODE_LATENCY_SPIKE", "数据接入延迟飙升", 0.78));
        graph.put("KG_NODE_DQ_TIMEOUT", fromDq);

        graph.put("KG_NODE_KAFKA_LAG", Collections.emptyList());
        graph.put("KG_NODE_LATENCY_SPIKE", Collections.emptyList());
        return graph;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<CausalReasoner.CausalEdge>> parseCausalGraph(Map<String, Object> graphMap) {
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
                        if (conf instanceof Number) edge.setConfidence(((Number) conf).doubleValue());
                        edges.add(edge);
                    }
                }
            }
            graph.put(entry.getKey(), edges);
        }
        return graph;
    }

    public OptimizeResponse doOptimize(OptimizeRequest request) {
        OptimizationProblem problem = request.getProblem();
        NsgaIIOptimizer.ConstraintChecker constraintChecker = vars -> true;
        NsgaIIOptimizer.ObjectiveEvaluator evaluator = buildMetricEvaluator(problem);

        NsgaIIOptimizer.ParetoFrontResult result =
                nsgaIIOptimizer.optimize(request, constraintChecker, evaluator);

        OptimizeResponse response = new OptimizeResponse();
        response.setFrontId("PF-" + Instant.now().toString().replace(":", "").replace("-", "").substring(0, 15));
        response.setGenerations(result.getGenerations());
        response.setElapsedMs(result.getElapsedMs());
        response.setConvergedAt(result.getConvergedAt() > 0 ? result.getConvergedAt() : null);

        List<ParetoSolution> solutions = result.getSolutions();
        int idx = 0;
        for (ParetoSolution sol : solutions) {
            sol.setSolutionId("SOL-" + String.format("%03d", ++idx));
            if (sol.getFeasibility() == null) sol.setFeasibility("FEASIBLE");
        }
        response.setParetoFront(solutions);

        ProblemSummary summary = new ProblemSummary();
        summary.setVariableCount(problem.getVariables() != null ? problem.getVariables().size() : 0);
        summary.setConstraintCount(problem.getConstraints() != null ? problem.getConstraints().size() : 0);
        summary.setObjectiveCount(problem.getObjectives() != null ? problem.getObjectives().size() : 0);
        summary.setFeasibleSolutions(solutions.size());
        summary.setInfeasibleSolutions(0);
        response.setProblemSummary(summary);
        return response;
    }

    private NsgaIIOptimizer.ObjectiveEvaluator buildMetricEvaluator(OptimizationProblem problem) {
        List<Variable> variables = problem.getVariables();
        List<Objective> objectives = problem.getObjectives();
        int numVars = variables.size();
        int numObjs = objectives.size();

        return vars -> {
            double[] objValues = new double[numObjs];
            for (int i = 0; i < numObjs; i++) {
                Objective obj = objectives.get(i);
                double val = 0;
                for (int j = 0; j < numVars; j++) {
                    Variable v = variables.get(j);
                    double normVal = normalizeVarValue(vars[j], v);
                    String oName = obj.getName() != null ? obj.getName().toLowerCase() : "";
                    if (oName.contains("throughput") || oName.contains("吞吐")) {
                        val += normVal * 1000.0;
                    } else if (oName.contains("cost") || oName.contains("成本")) {
                        val += normVal * 5.0;
                    } else if (oName.contains("latency") || oName.contains("延迟")) {
                        val += (1.0 - normVal) * 200;
                    } else {
                        val += normVal * 100.0;
                    }
                    if (j > 0) val += 0.1 * vars[j] * vars[j - 1] / 100.0;
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
            catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }

    public ExecutionPlan createExecutionPlan(CreatePlanRequest request) {
        ExecutionPlan plan = new ExecutionPlan();
        String planId = "PLAN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        plan.setPlanId(planId);
        plan.setName(request.getName());
        plan.setPriority(request.getPriority() != null ? request.getPriority() : "P2");
        plan.setStatus("PENDING");
        plan.setSource(request.getSource());

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

        PlanProgress progress = new PlanProgress();
        progress.setTotalSteps(steps.size());
        progress.setCompletedSteps(0);
        progress.setPercentage(0);
        plan.setProgress(progress);
        plan.setEstimatedDurationMs(60_000L);
        plan.setCreatedAt(ISO_FMT.format(Instant.now()));
        plan.setUpdatedAt(plan.getCreatedAt());

        persistPlan(plan);
        return plan;
    }

    public ExecutionPlan getExecutionPlan(String planId) {
        ExecutionPlan plan = loadPlanFromDb(planId);
        if (plan == null) {
            plan = new ExecutionPlan();
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
            plan.setCreatedAt(ISO_FMT.format(Instant.now()));
        }
        return plan;
    }

    private void persistPlan(ExecutionPlan plan) {
        try {
            jdbc.update(
                    "INSERT INTO ecos_execution_plan (plan_id, name, priority, status, source, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, NOW(), NOW()) " +
                    "ON CONFLICT (plan_id) DO UPDATE SET status=EXCLUDED.status, updated_at=NOW()",
                    plan.getPlanId(), plan.getName(), plan.getPriority(), plan.getStatus(),
                    plan.getSource() != null ? plan.getSource() : "");
        } catch (Exception e) {
            log.warn("Failed to persist plan {}: {}", plan.getPlanId(), e.getMessage());
        }
    }

    private ExecutionPlan loadPlanFromDb(String planId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM ecos_execution_plan WHERE plan_id = ?", planId);
            if (rows.isEmpty()) return null;
            Map<String, Object> row = rows.get(0);
            ExecutionPlan plan = new ExecutionPlan();
            plan.setPlanId((String) row.get("plan_id"));
            plan.setName((String) row.get("name"));
            plan.setPriority((String) row.get("priority"));
            plan.setStatus((String) row.get("status"));
            PlanSource src = new PlanSource();
            src.setType(String.valueOf(row.getOrDefault("source_type", "manual")));
            plan.setSource(src);
            plan.setSteps(Collections.emptyList());
            PlanProgress progress = new PlanProgress();
            progress.setTotalSteps(0);
            progress.setCompletedSteps(0);
            progress.setPercentage(0);
            plan.setProgress(progress);
            return plan;
        } catch (Exception e) {
            log.debug("Failed to load plan {}: {}", planId, e.getMessage());
            return null;
        }
    }

    private int parseOptionInt(Map<String, Object> options, String key, int defaultValue) {
        if (options == null) return defaultValue;
        Object val = options.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); }
            catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private double parseOptionDouble(Map<String, Object> options, String key, double defaultValue) {
        if (options == null) return defaultValue;
        Object val = options.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble((String) val); }
            catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}
