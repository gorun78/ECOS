package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.scenario.CognitionReadGateway;
import com.chinacreator.gzcm.workspace.scenario.CognitiveEngineUnavailableException;
import com.chinacreator.gzcm.workspace.scenario.WorkspaceSecurityAudit;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认知四端点（读侧 GET，卷07 F07-12 N4~N7，REQ-API-02）。
 * <pre>
 * GET /api/v1/business/scenarios/{id}/cognition/detect
 * GET /api/v1/business/scenarios/{id}/cognition/operation-eval
 * GET /api/v1/business/scenarios/{id}/cognition/hypotheses
 * GET /api/v1/business/scenarios/{id}/cognition/beliefs
 * </pre>
 *
 * <p>与既有 POST {@code /{id}/cognitive/{diagnose|forecast|simulate|policy}}（触发型，A12~A15）并存不改动（R-28）。
 * 引擎不可用 → 抛 {@link CognitiveEngineUnavailableException} → 真实 503（A3 红线，禁 200 空 body）。</p>
 */
@RestController
@RequestMapping("/api/v1/business/scenarios")
public class ScenarioCognitionV2Controller {

    private final ObjectProvider<CognitionReadGateway> gatewayProvider;
    private final WorkspaceSecurityAudit audit;
    private final ScenarioReadGuard readGuard;

    public ScenarioCognitionV2Controller(ObjectProvider<CognitionReadGateway> gatewayProvider,
                                         WorkspaceSecurityAudit audit,
                                         ScenarioReadGuard readGuard) {
        this.gatewayProvider = gatewayProvider;
        this.audit = audit;
        this.readGuard = readGuard;
    }

    @GetMapping("/{id}/cognition/detect")
    public ApiResponse<Map<String, Object>> detect(@PathVariable("id") String scenarioId,
                                                   @RequestParam Map<String, Object> params) {
        return read("detect", scenarioId, params, "cognitive/detect");
    }

    @GetMapping("/{id}/cognition/operation-eval")
    public ApiResponse<Map<String, Object>> operationEval(@PathVariable("id") String scenarioId,
                                                          @RequestParam Map<String, Object> params) {
        return read("operation-eval", scenarioId, params, "cognitive/operation-eval");
    }

    @GetMapping("/{id}/cognition/hypotheses")
    public ApiResponse<Map<String, Object>> hypotheses(@PathVariable("id") String scenarioId,
                                                       @RequestParam Map<String, Object> params) {
        return read("hypotheses", scenarioId, params, "cognitive/hypotheses");
    }

    @GetMapping("/{id}/cognition/beliefs")
    public ApiResponse<Map<String, Object>> beliefs(@PathVariable("id") String scenarioId,
                                                    @RequestParam Map<String, Object> params) {
        return read("beliefs", scenarioId, params, "cognitive/beliefs");
    }

    private ApiResponse<Map<String, Object>> read(String kind, String scenarioId,
                                                  Map<String, Object> params, String resource) {
        readGuard.assertReadable("scenario_cognition");
        CognitionReadGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            throw new CognitiveEngineUnavailableException("cognition read gateway 未装配: " + kind);
        }
        Map<String, Object> safeParams = params == null ? new LinkedHashMap<>() : params;
        Map<String, Object> data;
        try {
            data = switch (kind) {
                case "detect" -> gateway.detect(scenarioId, safeParams);
                case "operation-eval" -> gateway.operationEval(scenarioId, safeParams);
                case "hypotheses" -> gateway.hypotheses(scenarioId, safeParams);
                case "beliefs" -> gateway.beliefs(scenarioId, safeParams);
                default -> throw new CognitiveEngineUnavailableException("未知认知读侧类型: " + kind);
            };
        } catch (CognitiveEngineUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new CognitiveEngineUnavailableException(kind + " 调用失败: " + e.getMessage());
        }
        audit.record("scenario:cognition:" + kind, scenarioId, sizeOf(data));
        return ApiResponse.success(data == null ? new LinkedHashMap<>() : data);
    }

    private int sizeOf(Map<String, Object> data) {
        return data == null ? 0 : data.size();
    }
}
