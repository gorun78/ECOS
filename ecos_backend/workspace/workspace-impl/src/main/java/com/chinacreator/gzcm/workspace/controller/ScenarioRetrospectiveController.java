package com.chinacreator.gzcm.workspace.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.analysis.ScenarioRetrospectiveService;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;

/**
 * 场景复盘端点（V367 批次 C）：{@code GET /api/v1/workspace/scenarios/{id}/question-runs/{runId}/retrospective}。
 *
 * <p>读守卫复用已登记实体键 {@code scenario_analyses}（未知键会 fail-closed 403）。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace")
public class ScenarioRetrospectiveController {

    private final ScenarioRetrospectiveService service;
    private final ScenarioReadGuard readGuard;

    public ScenarioRetrospectiveController(ScenarioRetrospectiveService service, ScenarioReadGuard readGuard) {
        this.service = service;
        this.readGuard = readGuard;
    }

    @GetMapping("/scenarios/{id}/question-runs/{runId}/retrospective")
    public ApiResponse<Map<String, Object>> retrospective(@PathVariable("id") String scenarioId,
                                                          @PathVariable("runId") String runId) {
        readGuard.assertReadable("scenario_analyses");
        return ApiResponse.success(service.build(scenarioId, runId));
    }
}
