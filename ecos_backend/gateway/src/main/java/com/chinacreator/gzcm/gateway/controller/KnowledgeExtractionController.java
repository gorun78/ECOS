package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.service.KnowledgeExtractorService;
import com.chinacreator.gzcm.engine.ai.service.ExtractionConfig;
import com.chinacreator.gzcm.engine.kb.repository.ComplianceRuleMapper;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.chinacreator.gzcm.engine.cognitive2.service.KnowledgeReasonerService;
import com.chinacreator.gzcm.engine.cognitive2.service.RuleCausalService;
import com.chinacreator.gzcm.engine.cognitive2.service.RuleImpactService;
import com.chinacreator.gzcm.engine.cognitive2.model.SubQuery;
import com.chinacreator.gzcm.engine.cognitive2.model.SubQuery.SubQueryType;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 知识抽取 + 推理聚合 Controller（Gateway 层）
 *
 * 聚合端点:
 *   知识抽取 → ai-engine KnowledgeExtractorService
 *   规则管理 → kb-engine ComplianceRuleMapper (直接复用CRUD)
 *   推理    → cognitive-engine KnowledgeReasonerService + Causal + Impact
 */
@RestController
public class KnowledgeExtractionController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeExtractionController.class);

    @Autowired(required = false)
    private KnowledgeExtractorService knowledgeExtractorService;

    @Autowired(required = false)
    private ComplianceRuleMapper complianceRuleMapper;

    @Autowired(required = false)
    private KnowledgeReasonerService knowledgeReasonerService;

    @Autowired(required = false)
    private RuleCausalService ruleCausalService;

    @Autowired(required = false)
    private RuleImpactService ruleImpactService;

    // ── 知识抽取 ──

    @PostMapping("/api/v1/knowledge/extract")
    public ApiResponse<Map<String, Object>> extract(@RequestBody Map<String, Object> body) {
        if (knowledgeExtractorService == null) {
            return ApiResponse.badRequest("KnowledgeExtractorService not available");
        }
        try {
            String sourceType = (String) body.getOrDefault("sourceType", "MANUAL");
            Object configObj = body.get("config");
            String content = (String) body.get("content");

            Map<String, Object> config = configObj instanceof Map ? (Map<String, Object>) configObj : Collections.emptyMap();
            if (content == null || content.isEmpty()) {
                return ApiResponse.badRequest("content is required");
            }

            ExtractionConfig extConfig = new ExtractionConfig();
            extConfig.setDomain((String) config.getOrDefault("domain", ""));
            extConfig.setSyncMode(config.getOrDefault("syncMode", true).toString().equalsIgnoreCase("true") ? "AUTO" : "MANUAL");

            ExtractedSubGraph subGraph = knowledgeExtractorService.extract(content, extConfig);

            Map<String, Object> result = new HashMap<>();
            result.put("subGraph", subGraph);
            result.put("entityCount", subGraph.getEntities().size());
            result.put("relationCount", subGraph.getRelations().size());
            result.put("ruleCount", subGraph.getRules().size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("extract failed: {}", e.getMessage(), e);
            return ApiResponse.internalError("Extraction failed: " + e.getMessage());
        }
    }

    @GetMapping("/api/v1/knowledge/extract/sources")
    public ApiResponse<List<String>> extractSources() {
        return ApiResponse.success(Arrays.asList("MANUAL", "KB_ARTICLE", "DOCUMENT", "KG_ENTITY", "STRUCTURED"));
    }

    @GetMapping("/api/v1/knowledge/extract/history")
    public ApiResponse<List<Map<String, Object>>> extractHistory() {
        // stub: return empty list until ExtractionSource loader is ready
        return ApiResponse.success(Collections.emptyList());
    }

    // ── 规则判定 ──

    @PostMapping("/api/v1/rules/check")
    public ApiResponse<Map<String, Object>> checkRules(@RequestBody Map<String, Object> body) {
        try {
            String objectType = (String) body.get("objectType");
            String objectId = (String) body.get("objectId");
            Map<String, Object> facts = body.get("facts") instanceof Map ? (Map<String, Object>) body.get("facts") : Collections.emptyMap();

            // 查找匹配规则
            List<ComplianceRule> rules = complianceRuleMapper != null
                ? complianceRuleMapper.findByDomain(objectType) : Collections.emptyList();

            List<Map<String, Object>> verdicts = new ArrayList<>();
            for (ComplianceRule rule : rules) {
                boolean passed = evaluateRule(rule, facts);
                Map<String, Object> verdict = new HashMap<>();
                verdict.put("ruleId", rule.getId());
                verdict.put("ruleName", rule.getName());
                verdict.put("passed", passed);
                verdict.put("condition", rule.getCondition());
                verdict.put("action", rule.getAction());
                verdicts.add(verdict);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("objectType", objectType);
            result.put("objectId", objectId);
            result.put("verdicts", verdicts);
            result.put("totalChecked", verdicts.size());
            result.put("passed", verdicts.stream().filter(v -> (boolean) v.get("passed")).count());
            result.put("failed", verdicts.stream().filter(v -> !(boolean) v.get("passed")).count());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("rule check failed: {}", e.getMessage());
            return ApiResponse.internalError("Rule check failed: " + e.getMessage());
        }
    }

    @GetMapping("/api/v1/rules/causal-chain/{ruleId}")
    public ApiResponse<Object> causalChain(@PathVariable String ruleId) {
        if (ruleCausalService == null) return ApiResponse.badRequest("RuleCausalService not available");
        try {
            return ApiResponse.success(ruleCausalService.getCausalChain(ruleId));
        } catch (Exception e) {
            return ApiResponse.internalError(e.getMessage());
        }
    }

    @PostMapping("/api/v1/rules/impact-analysis")
    public ApiResponse<Object> impactAnalysis(@RequestBody Map<String, Object> body) {
        if (ruleImpactService == null) return ApiResponse.badRequest("RuleImpactService not available");
        String ruleId = (String) body.get("ruleId");
        try {
            return ApiResponse.success(ruleImpactService.analyze(ruleId));
        } catch (Exception e) {
            return ApiResponse.internalError(e.getMessage());
        }
    }

    @GetMapping("/api/v1/rules/audit-logs")
    public ApiResponse<List<Map<String, Object>>> auditLogs() {
        return ApiResponse.success(Collections.emptyList());
    }

    // ── 混合推理 ──

    @PostMapping("/api/v1/knowledge/reason")
    public ApiResponse<Object> reason(@RequestBody Map<String, Object> body) {
        if (knowledgeReasonerService == null) return ApiResponse.badRequest("KnowledgeReasonerService not available");
        try {
            String query = (String) body.get("query");
            Map<String, Object> ctx = body.get("context") instanceof Map ? (Map<String, Object>) body.get("context") : Collections.emptyMap();

            SubQuery sq = new SubQuery();
            sq.setType(SubQueryType.HYBRID);
            sq.setObjectType((String) ctx.get("objectType"));
            sq.setFacts(ctx);
            sq.setSemanticQuery(query);

            return ApiResponse.success(knowledgeReasonerService.reason(sq));
        } catch (Exception e) {
            log.error("reason failed: {}", e.getMessage());
            return ApiResponse.internalError(e.getMessage());
        }
    }

    // ── Helpers ──

    private boolean evaluateRule(ComplianceRule rule, Map<String, Object> facts) {
        // Simplified: check if rule condition keywords exist in facts
        if (rule.getCondition() == null || rule.getCondition().isEmpty()) return true;
        String cond = rule.getCondition().toLowerCase();
        for (Map.Entry<String, Object> fact : facts.entrySet()) {
            String factExpr = fact.getKey().toLowerCase() + "==" + String.valueOf(fact.getValue()).toLowerCase();
            if (cond.contains(fact.getKey().toLowerCase())) {
                // simple keyword matching
                if (String.valueOf(fact.getValue()).equalsIgnoreCase("true") && cond.contains("true")) return true;
                if (String.valueOf(fact.getValue()).equalsIgnoreCase("false") && cond.contains("false")) return false;
            }
        }
        return !cond.contains("false") || facts.values().stream().anyMatch(v -> String.valueOf(v).equalsIgnoreCase("false"));
    }
}
