package com.chinacreator.gzcm.engine.cognitive.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.ActionBridgeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cognitive")
public class ActionBridgeController {

    private final ActionBridgeService actionBridgeService;

    public ActionBridgeController(ActionBridgeService actionBridgeService) {
        this.actionBridgeService = actionBridgeService;
    }

    @PostMapping("/execute-action")
    public ApiResponse<Map<String, Object>> executeAction(@RequestBody Map<String, Object> req) {
        Map<String, Object> result = actionBridgeService.matchAndExecute(req);
        return ApiResponse.success(result);
    }

    @GetMapping("/available-actions")
    public ApiResponse<List<Map<String, Object>>> availableActions() {
        List<Map<String, Object>> actions = actionBridgeService.getAvailableActions();
        return ApiResponse.success(actions);
    }
}
