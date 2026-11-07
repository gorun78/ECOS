package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.scenario.ScenarioStatusTransitionService;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 场景状态迁移端点（卷07 F07-07 N12，真实 409 依赖 F07-13 advice）。
 * <pre>PATCH /api/v1/workspace/scenarios/{id}/status  body:{status}</pre>
 * <p>请求体自填 userId 一律忽略（主体取自 JWT），破 X-70 的服务面对应。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/scenarios")
public class ScenarioStatusController {

    private final ScenarioStatusTransitionService transitionService;

    public ScenarioStatusController(ScenarioStatusTransitionService transitionService) {
        this.transitionService = transitionService;
    }

    /** 状态迁移请求体（DI-2 强类型：键面 = 旧 Map 的 {@code status}，未知键按旧行为忽略）。 */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ScenarioStatusRequest(String status) {
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ScenarioStatusTransitionService.TransitionResult> transition(
            @PathVariable("id") String scenarioId,
            @RequestBody(required = false) ScenarioStatusRequest body) {
        String target = body == null ? null : body.status();
        return ApiResponse.success(transitionService.transition(scenarioId, target));
    }
}
