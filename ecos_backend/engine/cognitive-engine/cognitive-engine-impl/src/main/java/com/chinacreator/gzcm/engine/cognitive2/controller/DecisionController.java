package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.DecisionService;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitiveDecisionVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.DecisionCheckVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.DecisionImpactVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.DecisionLinkResultVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.DecisionRecordResultVO;
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
 *
 * <p><b>显式 bean name</b>：{@code @RestController("cognitiveDecisionController")}。
 * 与 {@code com.chinacreator.gzcm.engine.security.controller.DecisionController}
 * （bean 名 {@code decisionController}，详细设计-01 F01-02 / C.1[3] / W33 ·
 * 三通道唯一裁决端点 {@code POST /api/v1/security/decision}）默认同 simple-name
 * 冲突。gateway 单 fat-JAR 内 cognitive + security 均命中 ComponentScan，
 * 不显式命名会触发 {@code ConflictingBeanDefinitionException}（2026-10-02 Gate C 实测）。
 * 双端点各司其职（认知决策智能记录 vs. 三通道安全裁决），均保留。
 */
@RestController("cognitiveDecisionController")
@RequestMapping("/api/v1/cognitive/decision")
public class DecisionController {

    private static final Logger log = LoggerFactory.getLogger(DecisionController.class);

    @Autowired
    private DecisionService decisionService;

    /** POST /record — 记录决策 */
    @PostMapping("/record")
    public ApiResponse<DecisionRecordResultVO> record(@RequestBody Map<String, Object> body) {
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

            return ApiResponse.success(new DecisionRecordResultVO(id));
        } catch (Exception e) {
            log.error("Failed to record decision", e);
            return ApiResponse.internalError("Failed to record decision");
        }
    }

    /** POST /{id}/link — 添加因果关系 */
    @PostMapping("/{id}/link")
    public ApiResponse<DecisionLinkResultVO> link(@PathVariable String id,
                                                  @RequestBody Map<String, Object> body) {
        try {
            String targetId = (String) body.get("targetId");
            String relationship = (String) body.getOrDefault("relationship", "causes");

            if (targetId == null || targetId.isEmpty()) {
                return ApiResponse.badRequest("targetId is required");
            }

            decisionService.addCausalRelationship(id, targetId, relationship);

            return ApiResponse.success(new DecisionLinkResultVO(true, id, targetId));
        } catch (Exception e) {
            log.error("Failed to add causal relationship", e);
            return ApiResponse.internalError("Failed to link");
        }
    }

    /** GET /similar?query=... — 查找相似决策 */
    @GetMapping("/similar")
    public ApiResponse<List<CognitiveDecisionVO>> similar(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int maxResults) {
        try {
            List<Decision> decisions = decisionService.findSimilarDecisions(query, maxResults);
            var data = new ArrayList<CognitiveDecisionVO>();
            for (Decision d : decisions) {
                data.add(CognitiveDecisionVO.of(d));
            }
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Failed to find similar decisions", e);
            return ApiResponse.internalError("Failed to find similar");
        }
    }

    /** GET /{id}/chain — 追溯决策因果祖先链 */
    @GetMapping("/{id}/chain")
    public ApiResponse<List<CognitiveDecisionVO>> chain(@PathVariable String id) {
        try {
            List<Decision> decisions = decisionService.traceDecisionChain(id);
            var data = new ArrayList<CognitiveDecisionVO>();
            for (Decision d : decisions) {
                data.add(CognitiveDecisionVO.of(d));
            }
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Failed to trace decision chain", e);
            return ApiResponse.internalError("Failed to trace chain");
        }
    }

    /** GET /{id}/impact — 分析下游影响图 */
    @GetMapping("/{id}/impact")
    public ApiResponse<DecisionImpactVO> impact(@PathVariable String id) {
        try {
            var impact = decisionService.analyzeDecisionImpact(id);
            return ApiResponse.success(DecisionImpactVO.of(impact));
        } catch (Exception e) {
            log.error("Failed to analyze impact", e);
            return ApiResponse.internalError("Failed to analyze impact");
        }
    }

    /** POST /{id}/check-rules — 策略合规门检查 */
    @PostMapping("/{id}/check-rules")
    public ApiResponse<DecisionCheckVO> checkRules(@PathVariable String id) {
        try {
            var result = decisionService.checkDecisionRules(id);
            return ApiResponse.success(DecisionCheckVO.of(result));
        } catch (Exception e) {
            log.error("Failed to check decision rules", e);
            return ApiResponse.internalError("Failed to check rules");
        }
    }

}
