package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.ReasonerResult;
import com.chinacreator.gzcm.engine.cognitive2.model.SubQuery;
import com.chinacreator.gzcm.engine.cognitive2.model.SubQuery.SubQueryType;
import com.chinacreator.gzcm.engine.kb.KnowledgeRetrievalService;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.chinacreator.gzcm.engine.kb.repository.ComplianceRuleMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * KAG Reasoner 的 ECOS 实现 — 混合检索推理引擎。
 *
 * <p>混合检索策略：根据子问题类型动态选择执行路径
 * <ul>
 *   <li>{@link SubQueryType#KG_QUERY} — 知识图谱 Cypher 查询（Neo4j）</li>
 *   <li>{@link SubQueryType#RULE_CHECK} — 合规规则匹配 + 条件逐条评估</li>
 *   <li>{@link SubQueryType#VECTOR_RAG} — 向量检索 + RAG 生成</li>
 *   <li>{@link SubQueryType#HYBRID} — KG + 规则 + RAG 融合</li>
 * </ul>
 *
 * <p>跨模块依赖：
 * <ul>
 *   <li>{@link ComplianceRuleMapper} — kb-engine-impl，规则 KG 查询</li>
 *   <li>{@link KnowledgeRetrievalService} — kb-engine-api，向量 RAG 检索</li>
 * </ul>
 */
@Service
public class KnowledgeReasonerService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeReasonerService.class);

    private final ComplianceRuleMapper ruleMapper;
    private final KnowledgeRetrievalService retrievalService;

    public KnowledgeReasonerService(ComplianceRuleMapper ruleMapper,
                                    KnowledgeRetrievalService retrievalService) {
        this.ruleMapper = ruleMapper;
        this.retrievalService = retrievalService;
    }

    /**
     * 混合检索推理入口。
     *
     * @param sq 子问题定义（类型 + 参数）
     * @return 推理结果（答案 + 子问题分解 + 检索策略 + 置信度 + 推理链）
     */
    public ReasonerResult reason(SubQuery sq) {
        log.info("KnowledgeReasoner reasoning: type={}, objectType={}", sq.getType(), sq.getObjectType());

        return switch (sq.getType()) {
            case KG_QUERY    -> executeKgQuery(sq);
            case RULE_CHECK  -> executeRuleCheck(sq);
            case VECTOR_RAG  -> executeVectorRag(sq);
            case HYBRID      -> executeHybrid(sq);
        };
    }

    // ──────────── KG_QUERY ────────────

    private ReasonerResult executeKgQuery(SubQuery sq) {
        log.info("KG_QUERY: cypher={}", sq.getCypher());

        Map<String, Object> strategies = new LinkedHashMap<>();
        strategies.put("type", "KG_QUERY");
        strategies.put("cypher", sq.getCypher());
        strategies.put("note", "Cypher execution delegated to Neo4j driver (placeholder)");

        // 当前为占位实现；完整实现需注入 Neo4j Driver 执行 Cypher
        ReasonerResult result = new ReasonerResult();
        result.setAnswer("KG query result for: " + sq.getCypher());
        result.setSubQueries(List.of(sq.getCypher()));
        result.setRetrievalStrategies(strategies);
        result.setConfidence(0.7);
        return result;
    }

    // ──────────── RULE_CHECK ────────────

    private ReasonerResult executeRuleCheck(SubQuery sq) {
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

        // 2. 逐条规则检查 condition 是否满足
        List<Map<String, String>> reasoningChain = new ArrayList<>();
        List<String> matchedRuleNames = new ArrayList<>();
        List<String> subQueries = new ArrayList<>();

        for (ComplianceRule rule : candidateRules) {
            subQueries.add("Check rule: " + rule.getName());
            boolean satisfied = evaluateCondition(rule, facts);

            Map<String, String> step = new LinkedHashMap<>();
            step.put("ruleId", rule.getId());
            step.put("ruleName", rule.getName());
            step.put("condition", rule.getCondition());
            step.put("action", rule.getAction());
            step.put("domain", rule.getDomain());
            step.put("satisfied", String.valueOf(satisfied));
            reasoningChain.add(step);

            if (satisfied) {
                matchedRuleNames.add(rule.getName());
            }
        }

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

        ReasonerResult result = new ReasonerResult();
        result.setAnswer(answer);
        result.setSubQueries(subQueries);
        result.setRetrievalStrategies(strategies);
        result.setConfidence(confidence);
        result.setReasoningChain(reasoningChain);
        return result;
    }

    /**
     * 评估单条规则的条件是否满足。
     * 当前实现：简单字符串匹配；后续可升级为 SpEL 表达式引擎。
     */
    private boolean evaluateCondition(ComplianceRule rule, Map<String, Object> facts) {
        String condition = rule.getCondition();
        if (condition == null || condition.isEmpty()) {
            // 无条件 → 默认适用
            return true;
        }
        if (facts == null || facts.isEmpty()) {
            // 无事实上下文 → 无法判定，默认不适用
            return false;
        }

        // 简单匹配：检查 condition 中的 key 是否在 facts 中出现
        for (String key : facts.keySet()) {
            if (condition.contains(key)) {
                Object factValue = facts.get(key);
                if (factValue != null && condition.contains(String.valueOf(factValue))) {
                    return true;
                }
            }
        }
        return false;
    }

    // ──────────── VECTOR_RAG ────────────

    private ReasonerResult executeVectorRag(SubQuery sq) {
        String semanticQuery = sq.getSemanticQuery();
        log.info("VECTOR_RAG: semanticQuery={}", semanticQuery);

        // 复用现有向量服务
        Map<String, Object> ragResult = retrievalService.ragQuery(
                semanticQuery != null ? semanticQuery : sq.getObjectType(),
                5,  // topK
                0.7 // threshold
        );

        Map<String, Object> strategies = new LinkedHashMap<>();
        strategies.put("type", "VECTOR_RAG");
        strategies.put("semanticQuery", semanticQuery);
        strategies.put("ragResult", ragResult);

        ReasonerResult result = new ReasonerResult();
        result.setAnswer("RAG 检索完成，召回 " + ragResult.getOrDefault("documents", "0") + " 条文档。");
        result.setSubQueries(List.of("RAG: " + semanticQuery));
        result.setRetrievalStrategies(strategies);
        result.setConfidence(0.6);
        return result;
    }

    // ──────────── HYBRID ────────────

    private ReasonerResult executeHybrid(SubQuery sq) {
        log.info("HYBRID: objectType={}, semanticQuery={}", sq.getObjectType(), sq.getSemanticQuery());

        // 1. KG 查询
        ReasonerResult kgResult = executeKgQuery(sq);

        // 2. 规则检查
        ReasonerResult ruleResult = executeRuleCheck(sq);

        // 3. RAG 检索
        ReasonerResult ragResult = executeVectorRag(sq);

        // 4. 融合结果
        List<String> allSubQueries = new ArrayList<>();
        allSubQueries.addAll(kgResult.getSubQueries());
        allSubQueries.addAll(ruleResult.getSubQueries());
        allSubQueries.addAll(ragResult.getSubQueries());

        Map<String, Object> strategies = new LinkedHashMap<>();
        strategies.put("type", "HYBRID");
        strategies.put("kg", kgResult.getRetrievalStrategies());
        strategies.put("rule", ruleResult.getRetrievalStrategies());
        strategies.put("rag", ragResult.getRetrievalStrategies());

        double avgConfidence = (kgResult.getConfidence() + ruleResult.getConfidence() + ragResult.getConfidence()) / 3.0;

        StringBuilder answer = new StringBuilder();
        answer.append("混合检索结果：\n");
        answer.append("  [KG] ").append(kgResult.getAnswer()).append("\n");
        answer.append("  [规则] ").append(ruleResult.getAnswer()).append("\n");
        answer.append("  [RAG] ").append(ragResult.getAnswer());

        ReasonerResult result = new ReasonerResult();
        result.setAnswer(answer.toString());
        result.setSubQueries(allSubQueries);
        result.setRetrievalStrategies(strategies);
        result.setConfidence(Math.round(avgConfidence * 100.0) / 100.0);
        result.setReasoningChain(ruleResult.getReasoningChain());
        return result;
    }
}
