package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.scenario.ActionProposal;
import com.chinacreator.gzcm.workspace.scenario.ScenarioActionService;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景动作闭环端点（卷07 F07-10 N8~N11；只建读侧契约 + 写侧校验骨架，闭环终验属 09 册）。
 * <pre>
 * POST /api/v1/workspace/scenarios/{id}/actions
 * GET  /api/v1/workspace/scenarios/{id}/actions
 * GET  /api/v1/workspace/scenarios/{id}/decisions
 * POST /api/v1/workspace/actions/{actionId}/approve
 * </pre>
 * <p>审批人主体取自 JWT，绝不取请求体 userId（禁前端伪造）。</p>
 * <p>F07-18：读侧（actions/decisions）经 security-engine RLS/CLS 裁决，默认 DENY。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace")
public class ScenarioActionController {

    private final ScenarioActionService actionService;
    private final ScenarioReadGuard readGuard;

    public ScenarioActionController(ScenarioActionService actionService,
                                    ScenarioReadGuard readGuard) {
        this.actionService = actionService;
        this.readGuard = readGuard;
    }

    @PostMapping("/scenarios/{id}/actions")
    public ApiResponse<Map<String, Object>> createAction(@PathVariable("id") String scenarioId,
                                                          @RequestBody ActionProposal proposal) {
        return ApiResponse.success(actionService.propose(scenarioId, proposal, currentSubject()));
    }

    @GetMapping("/scenarios/{id}/actions")
    public ApiResponse<Map<String, Object>> listActions(@PathVariable("id") String scenarioId) {
        readGuard.assertReadable("scenario_action");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", readGuard.applyColumns("scenario_action", actionService.listActions(scenarioId)));
        return ApiResponse.success(data);
    }

    @GetMapping("/scenarios/{id}/decisions")
    public ApiResponse<Map<String, Object>> listDecisions(@PathVariable("id") String scenarioId) {
        readGuard.assertReadable("scenario_decision");
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> items = readGuard.applyColumns("scenario_decision",
                actionService.listDecisions(scenarioId));
        data.put("items", items);
        return ApiResponse.success(data);
    }

    @PostMapping("/actions/{actionId}/approve")
    public ApiResponse<Map<String, Object>> approve(@PathVariable("actionId") String actionId) {
        return ApiResponse.success(actionService.approve(actionId, currentSubject()));
    }

    private String currentSubject() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // fail-safe：主体缺失时由服务端校验判为 approver 缺失，而非伪造
        }
        return null;
    }
}
