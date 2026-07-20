package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.cognitive.impl.CausalReasoner;
import com.chinacreator.gzcm.cognitive.impl.NsgaIIOptimizer;
import com.chinacreator.gzcm.cognitive.impl.RuleEngine;
import com.chinacreator.gzcm.cognitive.model.*;
import com.chinacreator.gzcm.engine.ai.service.CognitiveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/cognitive")
public class CognitiveController {

    private static final Logger log = LoggerFactory.getLogger(CognitiveController.class);

    private final CognitiveService cognitiveService;
    private final RuleEngine ruleEngine;
    private final CausalReasoner causalReasoner;
    private final NsgaIIOptimizer nsgaIIOptimizer;

    public CognitiveController(CognitiveService cognitiveService,
                               RuleEngine ruleEngine,
                               CausalReasoner causalReasoner,
                               NsgaIIOptimizer nsgaIIOptimizer) {
        this.cognitiveService = cognitiveService;
        this.ruleEngine = ruleEngine;
        this.causalReasoner = causalReasoner;
        this.nsgaIIOptimizer = nsgaIIOptimizer;
    }

    @GetMapping("/blueprint")
    public ApiResponse<BlueprintResponse> getBlueprint(
            @RequestParam(required = false) String layer) {
        try {
            BlueprintResponse response = cognitiveService.buildBlueprintFromEngineStates(layer);
            log.info("GET /blueprint layer={} → overallScore={}", layer, response.getOverallScore());
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("蓝图查询失败: layer={}", layer, e);
            return ApiResponse.internalError("蓝图查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/reason")
    public ApiResponse<ReasonResponse> reason(@RequestBody ReasonRequest request) {
        try {
            String mode = request.getMode();
            if (mode == null || mode.isBlank()) return ApiResponse.badRequest("mode 不能为空");
            if (request.getFacts() == null || request.getFacts().isEmpty()) return ApiResponse.badRequest("facts 不能为空");

            long startMs = System.currentTimeMillis();
            ReasonResponse response;
            switch (mode.toLowerCase()) {
                case "rule":
                    response = cognitiveService.doRuleReason(request.getFacts(), request.getContext(), request.getOptions());
                    break;
                case "causal":
                    response = cognitiveService.doCausalReason(request.getFacts(), request.getContext(), request.getOptions());
                    break;
                default:
                    return ApiResponse.badRequest("mode 不在允许范围 [rule, causal]");
            }
            response.setElapsedMs(System.currentTimeMillis() - startMs);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("推理失败: mode={}", request.getMode(), e);
            return ApiResponse.internalError("推理失败: " + e.getMessage());
        }
    }

    @PostMapping("/optimize")
    public ApiResponse<OptimizeResponse> optimize(@RequestBody OptimizeRequest request) {
        try {
            if (request.getProblem() == null) return ApiResponse.badRequest("problem 不能为空");
            if (request.getProblem().getVariables() == null || request.getProblem().getVariables().isEmpty())
                return ApiResponse.badRequest("variables 不能为空");
            if (request.getProblem().getObjectives() == null || request.getProblem().getObjectives().isEmpty())
                return ApiResponse.badRequest("objectives 不能为空");

            OptimizeResponse response = cognitiveService.doOptimize(request);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("优化失败", e);
            return ApiResponse.internalError("优化失败: " + e.getMessage());
        }
    }

    @PostMapping("/plan")
    public ApiResponse<ExecutionPlan> createPlan(@RequestBody CreatePlanRequest request) {
        try {
            if (request == null || request.getName() == null || request.getName().isBlank())
                return ApiResponse.badRequest("name 不能为空");
            ExecutionPlan plan = cognitiveService.createExecutionPlan(request);
            log.info("POST /plan → planId={}", plan.getPlanId());
            return ApiResponse.success(plan);
        } catch (Exception e) {
            log.error("创建计划失败", e);
            return ApiResponse.internalError("创建计划失败: " + e.getMessage());
        }
    }

    @GetMapping("/plan/{id}")
    public ApiResponse<ExecutionPlan> getPlan(@PathVariable("id") String planId) {
        try {
            return ApiResponse.success(cognitiveService.getExecutionPlan(planId));
        } catch (Exception e) {
            log.error("查询计划失败: id={}", planId, e);
            return ApiResponse.internalError("查询计划失败: " + e.getMessage());
        }
    }

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
}
