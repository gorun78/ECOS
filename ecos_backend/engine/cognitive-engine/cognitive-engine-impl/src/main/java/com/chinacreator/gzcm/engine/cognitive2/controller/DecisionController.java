package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.DecisionService;
import com.chinacreator.gzcm.engine.cognitive2.model.Decision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 决策智能 REST API — 前缀 /api/v1/cognitive/decision
 *
 * <p>五步生命周期端点：record → link → query(similar/chain) → govern(impact/check-rules)</p>
 */
@RestController
@RequestMapping("/api/v1/cognitive/decision")
public class DecisionController {

    private static final Logger log = LoggerFactory.getLogger(DecisionController.class);

    @Autowired
    private DecisionService decisionService;

    /** POST /record — 记录决策 */
    @PostMapping("/record")
    public ApiResponse<Map<String, Object>> record(@RequestBody Map<String, Object> body) {
        try {
            String category = (String) body.get("category");
            String scenario = (String) body.get("scenario");
            String reasoning = (String) body.get("reasoning");
            String outcome = (String) body.get("outcome");
            double confidence = body.get("confidence") != null
                ? ((Number) body.get("confidence")).doubleValue() : 0.5;
            String decisionMaker = (String) body.get("decisionMaker");

            if (category == null || category.isEmpty()) {
                return ApiResponse.badRequest("category is required");
            }

            String id = decisionService.recordDecision(category, scenario, reasoning,
                                                       outcome, confidence, decisionMaker);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("decisionId", id);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to record decision", e);
            return ApiResponse.internalError("Failed to record decision: " + e.getMessage());
        }
    }

    /** POST /{id}/link — 添加因果关系 */
    @PostMapping("/{id}/link")
    public ApiResponse<Map<String, Object>> link(@PathVariable String id,
                                                  @RequestBody Map<String, Object> body) {
        try {
            String targetId = (String) body.get("targetId");
            String relationship = (String) body.getOrDefault("relationship", "causes");

            if (targetId == null || targetId.isEmpty()) {
                return ApiResponse.badRequest("targetId is required");
            }

            decisionService.addCausalRelationship(id, targetId, relationship);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("linked", true);
            result.put("sourceId", id);
            result.put("targetId", targetId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to add causal relationship", e);
            return ApiResponse.internalError("Failed to link: " + e.getMessage());
        }
    }

    /** GET /similar?query=... — 查找相似决策 */
    @GetMapping("/similar")
    public ApiResponse<List<Map<String, Object>>> similar(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int maxResults) {
        try {
            List<Decision> decisions = decisionService.findSimilarDecisions(query, maxResults);
            List<Map<String, Object>> data = new ArrayList<>();
            for (Decision d : decisions) {
                data.add(decisionToMap(d));
            }
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Failed to find similar decisions", e);
            return ApiResponse.internalError("Failed to find similar: " + e.getMessage());
        }
    }

    /** GET /{id}/chain — 追溯决策因果祖先链 */
    @GetMapping("/{id}/chain")
    public ApiResponse<List<Map<String, Object>>> chain(@PathVariable String id) {
        try {
            List<Decision> decisions = decisionService.traceDecisionChain(id);
            List<Map<String, Object>> data = new ArrayList<>();
            for (Decision d : decisions) {
                data.add(decisionToMap(d));
            }
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Failed to trace decision chain", e);
            return ApiResponse.internalError("Failed to trace chain: " + e.getMessage());
        }
    }

    /** GET /{id}/impact — 分析下游影响图 */
    @GetMapping("/{id}/impact")
    public ApiResponse<Map<String, Object>> impact(@PathVariable String id) {
        try {
            Map<String, Object> impact = decisionService.analyzeDecisionImpact(id);
            return ApiResponse.success(impact);
        } catch (Exception e) {
            log.error("Failed to analyze impact", e);
            return ApiResponse.internalError("Failed to analyze impact: " + e.getMessage());
        }
    }

    /** POST /{id}/check-rules — 策略合规门检查 */
    @PostMapping("/{id}/check-rules")
    public ApiResponse<Map<String, Object>> checkRules(@PathVariable String id) {
        try {
            Map<String, Object> result = decisionService.checkDecisionRules(id);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to check decision rules", e);
            return ApiResponse.internalError("Failed to check rules: " + e.getMessage());
        }
    }

    private Map<String, Object> decisionToMap(Decision d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("category", d.getCategory());
        m.put("scenario", d.getScenario());
        m.put("reasoning", d.getReasoning());
        m.put("outcome", d.getOutcome());
        m.put("confidence", d.getConfidence());
        m.put("decisionMaker", d.getDecisionMaker());
        m.put("createdAt", d.getCreatedAt());
        return m;
    }
}
