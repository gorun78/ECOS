package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.workspace.scenario.DcchengClient;
import com.chinacreator.gzcm.workspace.scenario.ScenarioMindService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioMindVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 场景认知四件套端点（PMO-60 v2.0 P3b T25）— 场景级跨服务代理。
 *
 * <p>读 mind 三要素 + 四件套配置 → 透传 cognitive-engine（现在跑 aiming:18084）——
 * workspace 自身不计算认知，只做 inline_context 组装与 REST 透传。</p>
 *
 * <p>端点前缀：{@code /api/v1/workspace/scenarios}</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/scenarios")
public class ScenarioCognitiveController {

    private static final Logger log = LoggerFactory.getLogger(ScenarioCognitiveController.class);

    private final DcchengClient cognitiveClient;
    private final ScenarioMindService mindService;

    public ScenarioCognitiveController(DcchengClient cognitiveClient, ScenarioMindService mindService) {
        this.cognitiveClient = cognitiveClient;
        this.mindService = mindService;
    }

    @PostMapping("/{id}/cognitive/diagnose")
    public ApiResponse<Map<String, Object>> diagnose(
            @PathVariable("id") String scenarioId,
            @RequestParam(value = "mind", required = false) String mindId) {
        Map<String, Object> payload = buildPayload(scenarioId, mindId);
        try {
            Map<String, Object> result = cognitiveClient.diagnose(payload);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.warn("cognitive/diagnose failed for scenario={}: {}", scenarioId, e.getMessage());
            return ApiResponse.badRequest("cognitive_service_unavailable: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/cognitive/forecast")
    public ApiResponse<Map<String, Object>> forecast(
            @PathVariable("id") String scenarioId,
            @RequestParam(value = "mind", required = false) String mindId) {
        Map<String, Object> payload = buildPayload(scenarioId, mindId);
        try {
            Map<String, Object> result = cognitiveClient.forecast(payload);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.warn("cognitive/forecast failed for scenario={}: {}", scenarioId, e.getMessage());
            return ApiResponse.badRequest("cognitive_service_unavailable: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/cognitive/simulate")
    public ApiResponse<Map<String, Object>> simulate(
            @PathVariable("id") String scenarioId,
            @RequestParam(value = "mind", required = false) String mindId,
            @RequestParam(value = "mindB", required = false) String mindBId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> payload = buildPayload(scenarioId, mindId);
        // A/B 对比推演：传 mindB 时带对比 context
        if (mindBId != null && !mindBId.isBlank()) {
            ScenarioMindVO mindB;
            try {
                mindB = mindService.getMind(Long.valueOf(mindBId));
            } catch (NumberFormatException nfe) {
                throw new BusinessException(400, "invalid_mindB_id: " + mindBId);
            }
            if (mindB != null) {
                payload.put("mindB_context", mindB.toInlineContext());
            }
        }
        if (body != null && !body.isEmpty()) {
            payload.put("counterfactors", body.getOrDefault("counterfactors", List.of()));
        }
        try {
            Map<String, Object> result = cognitiveClient.simulate(payload);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.warn("cognitive/simulate failed for scenario={}: {}", scenarioId, e.getMessage());
            return ApiResponse.badRequest("cognitive_service_unavailable: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/cognitive/policy")
    public ApiResponse<Map<String, Object>> policy(
            @PathVariable("id") String scenarioId,
            @RequestParam(value = "mind", required = false) String mindId) {
        Map<String, Object> payload = buildPayload(scenarioId, mindId);
        try {
            Map<String, Object> result = cognitiveClient.plan(payload);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.warn("cognitive/policy failed for scenario={}: {}", scenarioId, e.getMessage());
            return ApiResponse.badRequest("cognitive_service_unavailable: " + e.getMessage());
        }
    }

    private Map<String, Object> buildPayload(String scenarioId, String mindId) {
        ScenarioMindVO mind;
        if (mindId != null && !mindId.isBlank()) {
            try {
                mind = mindService.getMind(Long.valueOf(mindId));
            } catch (NumberFormatException nfe) {
                throw new BusinessException(400, "invalid_mind_id: " + mindId);
            }
        } else {
            mind = mindService.getActiveMind(scenarioId);
        }
        if (mind == null) {
            throw new BusinessException(404, "no_active_mind_for_scenario=" + scenarioId);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scenarioId", scenarioId);
        payload.put("mindId", mind.getId());
        payload.put("inlineContext", mind.toInlineContext());
        return payload;
    }
}
