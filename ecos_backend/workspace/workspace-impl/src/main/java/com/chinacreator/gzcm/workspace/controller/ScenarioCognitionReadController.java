package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.exception.CognitiveEngineUnavailableException;
import com.chinacreator.gzcm.workspace.scenario.DcchengClient;
import com.chinacreator.gzcm.workspace.scenario.ScenarioMindService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认知四端点读侧代理（详细设计-07 D-2 N4~N7 / F07-12 / C-158）。
 *
 * <p>REQ-API-02 契约：{@code /api/v1/business/scenarios/{id}/cognition/{detect|operation-eval|hypotheses|beliefs}}。
 * 语义 = <b>读取认知产物</b>（护栏 20 号 SI12 铁律：场景层不计算，经 gateway→cognitive 门面透传）。
 * 引擎不可用 → 503 原样穿透（A3 红线），前四旧 POST /cognitive/* 端点保持不动（只增不改）。</p>
 *
 * <p>判定约束：</p>
 * <ul>
 *   <li>{@code detect}（N4）：cognitive {@code /cognitive/diagnosis} 读侧，若引擎侧未提供该读侧端点 →
 *       客户端抛 {@code IllegalStateException}，advice 落真实 503（禁 200 空 body 伪装）；
 *   <li>{@code operation-eval}（N5）：cognitive {@code /cognitive/operation-eval} 读侧，同上；
 *   <li>{@code hypotheses}（N6）/ {@code beliefs}（N7）：cognitive 读侧真实存在（cognitive2 controller 已建）；
 * </ul>
 * 四端点统一经 {@link DcchengClient} 走 cognitive 基础 + 4xx/5xx/超时 → 503 抛。
 */
@RestController
public class ScenarioCognitionReadController {

    private final DcchengClient cognitiveClient;
    private final ScenarioMindService mindService;

    public ScenarioCognitionReadController(DcchengClient cognitiveClient,
                                           ScenarioMindService mindService) {
        this.cognitiveClient = cognitiveClient;
        this.mindService = mindService;
    }

    @Operation(operationId = "getCognitionDetect", summary = "getCognitionDetect")
    @GetMapping("/api/v1/business/scenarios/{id}/cognition/detect")
    public ApiResponse<JsonNode> detect(@PathVariable String id,
                                        @RequestParam(value = "mind", required = false) String mindId) {
        String mind = resolveMind(id, mindId);
        try {
            return ApiResponse.success(cognitiveClient.detect(id, mind));
        } catch (IllegalStateException e) {
            throw new CognitiveEngineUnavailableException("detect", e);
        }
    }

    @Operation(operationId = "getOperationEval", summary = "getOperationEval")
    @GetMapping("/api/v1/business/scenarios/{id}/cognition/operation-eval")
    public ApiResponse<JsonNode> operationEval(@PathVariable String id,
                                               @RequestParam(value = "mind", required = false) String mindId) {
        String mind = resolveMind(id, mindId);
        try {
            return ApiResponse.success(cognitiveClient.operationEval(id, mind));
        } catch (IllegalStateException e) {
            throw new CognitiveEngineUnavailableException("operation-eval", e);
        }
    }

    @Operation(operationId = "listHypotheses", summary = "listHypotheses")
    @GetMapping("/api/v1/business/scenarios/{id}/cognition/hypotheses")
    public ApiResponse<JsonNode> hypotheses(@PathVariable String id,
                                            @RequestParam(value = "mind", required = false) String mindId) {
        String mind = resolveMind(id, mindId);
        try {
            return ApiResponse.success(cognitiveClient.hypothesesGet(id, mind));
        } catch (IllegalStateException e) {
            throw new CognitiveEngineUnavailableException("hypothesis-list", e);
        }
    }

    @Operation(operationId = "listBeliefs", summary = "listBeliefs")
    @GetMapping("/api/v1/business/scenarios/{id}/cognition/beliefs")
    public ApiResponse<JsonNode> beliefs(@PathVariable String id,
                                         @RequestParam(value = "mind", required = false) String mindId) {
        String mind = resolveMind(id, mindId);
        try {
            return ApiResponse.success(cognitiveClient.beliefsGet(id, mind));
        } catch (IllegalStateException e) {
            throw new CognitiveEngineUnavailableException("belief-list", e);
        }
    }

    /** mind 解析：显式传参 -> 取；否则取场景 active mind。无 mind 时透传 null（cognitive 侧按 scenarioId 兜底）。 */
    private String resolveMind(String scenarioId, String mindId) {
        if (mindId != null && !mindId.isBlank()) {
            return mindId;
        }
        try {
            var active = mindService.getActiveMind(scenarioId);
            return active == null ? null : String.valueOf(active.getId());
        } catch (Exception e) {
            return null;
        }
    }
}
