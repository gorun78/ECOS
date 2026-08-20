package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.ReasonerResult;
import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningStep;
import com.chinacreator.gzcm.engine.cognitive2.model.SubQuery;
import com.chinacreator.gzcm.engine.cognitive2.model.SubQuery.SubQueryType;
import com.chinacreator.gzcm.engine.kb.KnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.KnowledgeRetrievalService;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.repository.ComplianceRuleMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * KAG Reasoner 的 ECOS 实现 — 混合检索推理引擎。
 *
 * <p>混合检索策略：根据子问题类型动态选择执行路径
 * <ul>
 *   <li>{@link SubQueryType#KG_QUERY} — 知识图谱 Cypher 查询（Neo4j，通过 KnowledgeGraphService）</li>
 *   <li>{@link SubQueryType#RULE_CHECK} — 合规规则匹配 + 条件逐条评估</li>
 *   <li>{@link SubQueryType#VECTOR_RAG} — 向量检索 + RAG 生成</li>
 *   <li>{@link SubQueryType#HYBRID} — KG + 规则 + RAG 并行融合（超时保护 + 加权排序）</li>
 * </ul>
 *
 * <p>跨模块依赖：
 * <ul>
 *   <li>{@link ComplianceRuleMapper} — kb-engine-impl，规则 KG 查询</li>
 *   <li>{@link KnowledgeRetrievalService} — kb-engine-api，向量 RAG 检索</li>
 *   <li>{@link KnowledgeGraphService} — kb-engine-api，知识图谱 Cypher 查询</li>
 * </ul>
 */
@Service
public class KnowledgeReasonerService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeReasonerService.class);

    /** HYBRID 模式每路超时（秒） */
    private static final long HYBRID_TIMEOUT_SEC = 10;

    /** 置信度权重 */
    private static final double KG_WEIGHT   = 0.80;
    private static final double RULE_WEIGHT = 0.85;
    private static final double RAG_WEIGHT  = 0.60;

    private final ComplianceRuleMapper ruleMapper;
    private final KnowledgeRetrievalService retrievalService;
    private final KnowledgeGraphService graphService;
    private final SpelConditionEvaluator spelEvaluator;
    private final ReasoningPathBuilder reasoningPathBuilder;

    public KnowledgeReasonerService(ComplianceRuleMapper ruleMapper,
                                    KnowledgeRetrievalService retrievalService,
                                    KnowledgeGraphService graphService,
                                    SpelConditionEvaluator spelEvaluator,
                                    ReasoningPathBuilder reasoningPathBuilder) {
        this.ruleMapper = ruleMapper;
        this.retrievalService = retrievalService;
        this.graphService = graphService;
        this.spelEvaluator = spelEvaluator;
        this.reasoningPathBuilder = reasoningPathBuilder;
    }

    /**
     * 混合检索推理入口。
     *
     * @param sq 子问题定义（类型 + 参数）
     * @return 推理结果（答案 + 子问题分解 + 检索策略 + 置信度 + 推理链 + 来源标注 + 耗时）
     */
    public ReasonerResult reason(SubQuery sq) {
        long start = System.currentTimeMillis();
        log.info("KnowledgeReasoner reasoning: type={}, objectType={}", sq.getType(), sq.getObjectType());

        ReasonerResult result = switch (sq.getType()) {
            case KG_QUERY    -> executeKgQuery(sq);
            case RULE_CHECK  -> executeRuleCheck(sq);
            case VECTOR_RAG  -> executeVectorRag(sq);
            case HYBRID      -> executeHybrid(sq);
        };

        result.setElapsedMs(System.currentTimeMillis() - start);
        return result;
    }

    // ──────────── KG_QUERY ────────────

    private ReasonerResult executeKgQuery(SubQuery sq) {
        long start = System.currentTimeMillis();
        String cypher = sq.getCypher();
        log.info("KG_QUERY: cypher={}", cypher);

        Map<String, Object> strategies = new LinkedHashMap<>();
        strategies.put("type", "KG_QUERY");
        strategies.put("cypher", cypher);

        ReasonerResult result = new ReasonerResult();

        try {
            List<KnowledgeNode> nodes = graphService.search(cypher);
            long latency = System.currentTimeMillis() - start;

            if (nodes == null || nodes.isEmpty()) {
                result.setAnswer("KG 查询未返回结果。Cypher: " + cypher);
                result.setConfidence(0.3);
                strategies.put("nodeCount", 0);
                strategies.put("note", "无匹配节点");
            } else {
                // 结构化：节点 → 自然语言答案
                StringBuilder answer = new StringBuilder("知识图谱查询结果（" + nodes.size() + " 个节点）：");
                for (int i = 0; i < nodes.size(); i++) {
                    KnowledgeNode node = nodes.get(i);
                    answer.append("\n  [").append(i + 1).append("] ").append(node.getLabel());
                    if (node.getNodeType() != null && !node.getNodeType().isEmpty()) {
                        answer.append(" (").append(node.getNodeType()).append(")");
                    }
                    if (node.getDescription() != null && !node.getDescription().isEmpty()) {
                        answer.append(": ").append(node.getDescription());
                    }
                }
                result.setAnswer(answer.toString());
                result.setConfidence(nodes.size() > 1 ? 0.75 : 0.7);
                strategies.put("nodeCount", nodes.size());
            }

            strategies.put("latencyMs", latency);

            // 来源标注
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("type", "KG");
            source.put("content", cypher);
            source.put("confidence", result.getConfidence());
            source.put("latency", latency);
            result.setSources(List.of(source));

        } catch (Exception e) {
            log.warn("KG_QUERY 执行失败: {}", e.getMessage());
            result.setAnswer("KG 查询异常: " + e.getMessage());
            result.setConfidence(0.0);
            strategies.put("error", e.getMessage());
            strategies.put("latencyMs", System.currentTimeMillis() - start);

            Map<String, Object> source = new LinkedHashMap<>();
            source.put("type", "KG");
            source.put("content", cypher);
            source.put("confidence", 0.0);
            source.put("latency", System.currentTimeMillis() - start);
            source.put("error", e.getMessage());
            result.setSources(List.of(source));
        }

        result.setSubQueries(List.of("KG: " + cypher));
        result.setRetrievalStrategies(strategies);
        return result;
    }

    // ──────────── RULE_CHECK ────────────

    private ReasonerResult executeRuleCheck(SubQuery sq) {
        long start = System.currentTimeMillis();
        String objectType = sq.getObjectType();
        Map<String, Object> facts = sq.getFacts();
        log.info("RULE_CHECK: objectType={}, facts={}", objectType, facts);

        // 1. KG 查询：通过 ComplianceRuleMapper 按 domain 匹配规则
        List<ComplianceRule> candidateRules;
        if (objectType != null && !objectType.isEmpty()) {
            candidateRules = ruleMapper.findByDomain(objectType);
        } else {
            candidateRules = ruleMapper.findAll();
        }

        // 过滤：仅已启用的规则
        candidateRules = candidateRules.stream()
                .filter(ComplianceRule::isEnabled)
                .collect(Collectors.toList());

        // 2. 逐条规则检查 condition 是否满足（PMO-35: SpEL 评估）
        List<Map<String, String>> reasoningChain = new ArrayList<>();
        List<String> matchedRuleNames = new ArrayList<>();
        List<String> subQueries = new ArrayList<>();
        List<ReasoningStep> reasoningSteps = new ArrayList<>();

        int stepIdx = 0;
        for (ComplianceRule rule : candidateRules) {
            subQueries.add("Check rule: " + rule.getName());
            SpelConditionEvaluator.EvalResult evalResult = evaluateCondition(rule, facts);
            boolean satisfied = evalResult.isSatisfied();

            Map<String, String> step = new LinkedHashMap<>();
            step.put("ruleId", rule.getId());
            step.put("ruleName", rule.getName());
            step.put("condition", rule.getCondition());
            step.put("action", rule.getAction());
            step.put("domain", rule.getDomain());
            step.put("satisfied", String.valueOf(satisfied));
            reasoningChain.add(step);

            // PMO-35: 构建结构化推理步骤
            ReasoningStep rStep = reasoningPathBuilder.buildStep(
                rule.getId(), rule.getName(), rule.getCondition(),
                rule.getAction(), facts, evalResult, stepIdx++
            );
            reasoningSteps.add(rStep);

            if (satisfied) {
                matchedRuleNames.add(rule.getName());
            }
        }

        long latency = System.currentTimeMillis() - start;

        // 3. 构建答案
        String answer;
        double confidence;
        if (matchedRuleNames.isEmpty()) {
            answer = "未匹配到适用规则。已检查 " + candidateRules.size() + " 条规则（domain=" + objectType + "）。";
            confidence = 0.3;
        } else {
            answer = "匹配到 " + matchedRuleNames.size() + " 条适用规则："
                    + String.join("；", matchedRuleNames) + "。详情见推理链。";
            confidence = 0.85;
        }

        Map<String, Object> strategies = new LinkedHashMap<>();
        strategies.put("type", "RULE_CHECK");
        strategies.put("domain", objectType);
        strategies.put("candidateRules", candidateRules.size());
        strategies.put("matchedRules", matchedRuleNames.size());
        strategies.put("latencyMs", latency);

        // 来源标注
        List<Map<String, Object>> sources = new ArrayList<>();
        for (String ruleName : matchedRuleNames) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("type", "RULE");
            source.put("content", ruleName);
            source.put("confidence", confidence);
            source.put("latency", latency);
            sources.add(source);
        }
        if (sources.isEmpty()) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("type", "RULE");
            source.put("content", "no match");
            source.put("confidence", confidence);
            source.put("latency", latency);
            sources.add(source);
        }

        ReasonerResult result = new ReasonerResult();
        result.setAnswer(answer);
        result.setSubQueries(subQueries);
        result.setRetrievalStrategies(strategies);
        result.setConfidence(confidence);
        result.setReasoningChain(reasoningChain);
        result.setSources(sources);
        // PMO-35: 结构化推理路径
        result.setReasoningPath(reasoningPathBuilder.buildPath(reasoningSteps, answer));
        return result;
    }

    /**
     * 评估单条规则的条件是否满足。
     * PMO-35: 用 SpEL 替换字符串 contains 匹配，旧格式自动降级。
     */
    private SpelConditionEvaluator.EvalResult evaluateCondition(ComplianceRule rule, Map<String, Object> facts) {
        String condition = rule.getCondition();
        if (condition == null || condition.isEmpty()) {
            // 无条件 → 默认适用
            return new SpelConditionEvaluator.EvalResult(true, "No condition, default applicable", Collections.emptyMap());
        }
        if (facts == null || facts.isEmpty()) {
            // 无事实上下文 → 无法判定，默认不适用
            return new SpelConditionEvaluator.EvalResult(false, "No facts provided", Collections.emptyMap());
        }
        return spelEvaluator.evaluate(condition, facts);
    }

    // ──────────── VECTOR_RAG ────────────

    private ReasonerResult executeVectorRag(SubQuery sq) {
        long start = System.currentTimeMillis();
        String semanticQuery = sq.getSemanticQuery();
        log.info("VECTOR_RAG: semanticQuery={}", semanticQuery);

        // 复用现有向量服务
        Map<String, Object> ragResult = retrievalService.ragQuery(
                semanticQuery != null ? semanticQuery : sq.getObjectType(),
                5,  // topK
                0.7 // threshold
        );

        long latency = System.currentTimeMillis() - start;

        Map<String, Object> strategies = new LinkedHashMap<>();
        strategies.put("type", "VECTOR_RAG");
        strategies.put("semanticQuery", semanticQuery);
        strategies.put("ragResult", ragResult);
        strategies.put("latencyMs", latency);

        ReasonerResult result = new ReasonerResult();
        result.setAnswer("RAG 检索完成，召回 " + ragResult.getOrDefault("documents", "0") + " 条文档。");
        result.setSubQueries(List.of("RAG: " + semanticQuery));
        result.setRetrievalStrategies(strategies);
        result.setConfidence(0.6);

        // 来源标注
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "RAG");
        source.put("content", semanticQuery);
        source.put("confidence", 0.6);
        source.put("latency", latency);
        result.setSources(List.of(source));

        return result;
    }

    // ──────────── HYBRID ────────────

    private ReasonerResult executeHybrid(SubQuery sq) {
        long hybridStart = System.currentTimeMillis();
        log.info("HYBRID: objectType={}, semanticQuery={}", sq.getObjectType(), sq.getSemanticQuery());

        // 并行三路执行（CompletableFuture + 超时10秒）
        CompletableFuture<ReasonerResult> kgFuture = CompletableFuture
                .supplyAsync(() -> executeKgQuery(sq))
                .orTimeout(HYBRID_TIMEOUT_SEC, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.warn("HYBRID KG 路超时或异常: {}", ex.getMessage());
                    return degradedResult("KG", ex, sq.getCypher() != null ? sq.getCypher() : "", KG_WEIGHT);
                });

        CompletableFuture<ReasonerResult> ruleFuture = CompletableFuture
                .supplyAsync(() -> executeRuleCheck(sq))
                .orTimeout(HYBRID_TIMEOUT_SEC, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.warn("HYBRID RULE 路超时或异常: {}", ex.getMessage());
                    return degradedResult("RULE", ex, sq.getObjectType(), RULE_WEIGHT);
                });

        CompletableFuture<ReasonerResult> ragFuture = CompletableFuture
                .supplyAsync(() -> executeVectorRag(sq))
                .orTimeout(HYBRID_TIMEOUT_SEC, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.warn("HYBRID RAG 路超时或异常: {}", ex.getMessage());
                    return degradedResult("RAG", ex, sq.getSemanticQuery(), RAG_WEIGHT);
                });

        // 等待全部完成（或降级）
        CompletableFuture.allOf(kgFuture, ruleFuture, ragFuture).join();

        ReasonerResult kgResult   = kgFuture.join();
        ReasonerResult ruleResult = ruleFuture.join();
        ReasonerResult ragResult  = ragFuture.join();

        // 汇总子查询
        List<String> allSubQueries = new ArrayList<>();
        if (kgResult.getSubQueries() != null)   allSubQueries.addAll(kgResult.getSubQueries());
        if (ruleResult.getSubQueries() != null) allSubQueries.addAll(ruleResult.getSubQueries());
        if (ragResult.getSubQueries() != null)  allSubQueries.addAll(ragResult.getSubQueries());

        // 汇总策略
        Map<String, Object> strategies = new LinkedHashMap<>();
        strategies.put("type", "HYBRID");
        strategies.put("kg", kgResult.getRetrievalStrategies());
        strategies.put("rule", ruleResult.getRetrievalStrategies());
        strategies.put("rag", ragResult.getRetrievalStrategies());

        // ── 融合排序 ──
        // 收集所有来源标注
        List<Map<String, Object>> allSources = new ArrayList<>();
        collectSources(allSources, kgResult.getSources());
        collectSources(allSources, ruleResult.getSources());
        collectSources(allSources, ragResult.getSources());

        // a. 按置信度加权
        for (Map<String, Object> source : allSources) {
            String type = (String) source.get("type");
            double rawConfidence = source.get("confidence") instanceof Number
                    ? ((Number) source.get("confidence")).doubleValue() : 0.0;
            double weight = getWeight(type);
            source.put("weightedConfidence", Math.round(rawConfidence * weight * 10000.0) / 10000.0);
        }

        // b. 去重：相同内容取最高加权置信度
        Map<String, Map<String, Object>> deduped = new LinkedHashMap<>();
        for (Map<String, Object> source : allSources) {
            String content = (String) source.getOrDefault("content", "");
            String key = source.get("type") + "::" + content;
            double wc = ((Number) source.get("weightedConfidence")).doubleValue();
            Map<String, Object> existing = deduped.get(key);
            if (existing == null) {
                deduped.put(key, source);
            } else {
                double existingWc = ((Number) existing.get("weightedConfidence")).doubleValue();
                if (wc > existingWc) {
                    deduped.put(key, source);
                }
            }
        }

        // c. 按加权置信度排序（高在前）
        List<Map<String, Object>> fusedSources = new ArrayList<>(deduped.values());
        fusedSources.sort((a, b) -> {
            double wcA = ((Number) a.get("weightedConfidence")).doubleValue();
            double wcB = ((Number) b.get("weightedConfidence")).doubleValue();
            return Double.compare(wcB, wcA);
        });

        // 计算融合置信度：加权平均
        double fusedConfidence = 0.0;
        double totalWeight = 0.0;
        if (kgResult.getConfidence() > 0) {
            fusedConfidence += kgResult.getConfidence() * KG_WEIGHT;
            totalWeight += KG_WEIGHT;
        }
        if (ruleResult.getConfidence() > 0) {
            fusedConfidence += ruleResult.getConfidence() * RULE_WEIGHT;
            totalWeight += RULE_WEIGHT;
        }
        if (ragResult.getConfidence() > 0) {
            fusedConfidence += ragResult.getConfidence() * RAG_WEIGHT;
            totalWeight += RAG_WEIGHT;
        }
        if (totalWeight > 0) {
            fusedConfidence = Math.round(fusedConfidence / totalWeight * 10000.0) / 10000.0;
        }

        // 构建答案
        StringBuilder answer = new StringBuilder();
        answer.append("混合检索结果：\n");
        answer.append("  [KG] ").append(kgResult.getAnswer()).append("\n");
        answer.append("  [规则] ").append(ruleResult.getAnswer()).append("\n");
        answer.append("  [RAG] ").append(ragResult.getAnswer());

        ReasonerResult result = new ReasonerResult();
        result.setAnswer(answer.toString());
        result.setSubQueries(allSubQueries);
        result.setRetrievalStrategies(strategies);
        result.setConfidence(fusedConfidence);
        result.setReasoningChain(ruleResult.getReasoningChain());
        result.setSources(fusedSources);
        result.setElapsedMs(System.currentTimeMillis() - hybridStart);
        return result;
    }

    // ── HYBRID 辅助方法 ──

    /** 获取来源类型对应的权重 */
    private double getWeight(String type) {
        return switch (type) {
            case "KG"   -> KG_WEIGHT;
            case "RULE" -> RULE_WEIGHT;
            case "RAG"  -> RAG_WEIGHT;
            default     -> 0.5;
        };
    }

    /** 收集来源标注到目标列表 */
    private void collectSources(List<Map<String, Object>> target, List<Map<String, Object>> sources) {
        if (sources != null) {
            for (Map<String, Object> s : sources) {
                target.add(new LinkedHashMap<>(s));
            }
        }
    }

    /** 构造降级结果（超时或异常时使用） */
    private ReasonerResult degradedResult(String type, Throwable ex, String context, double weight) {
        ReasonerResult r = new ReasonerResult();
        String reason = ex instanceof TimeoutException ? "超时（>" + HYBRID_TIMEOUT_SEC + "s）" : "异常: " + ex.getMessage();
        r.setAnswer("[" + type + " 降级] " + reason);
        r.setConfidence(0.0);
        r.setSubQueries(List.of(type + " degraded: " + context));
        r.setRetrievalStrategies(Map.of("type", type, "status", "degraded", "reason", reason));

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", type);
        source.put("content", context);
        source.put("confidence", 0.0);
        source.put("latency", HYBRID_TIMEOUT_SEC * 1000);
        source.put("degraded", true);
        source.put("reason", reason);
        r.setSources(List.of(source));
        return r;
    }
}
