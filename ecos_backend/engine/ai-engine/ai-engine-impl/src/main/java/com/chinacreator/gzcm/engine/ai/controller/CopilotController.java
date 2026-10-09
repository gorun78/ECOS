package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.service.ICopilotAgentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agent/copilot")
public class CopilotController {

    private final ICopilotAgentService copilotAgentService;

    public CopilotController(ICopilotAgentService copilotAgentService) {
        this.copilotAgentService = copilotAgentService;
    }

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> request) {
        String agentId = (String) request.getOrDefault("agentId", "agent-data");
        String message = (String) request.get("message");
        String sessionId = (String) request.getOrDefault("sessionId", UUID.randomUUID().toString());

        if (message == null || message.isBlank()) {
            return ApiResponse.badRequest("message 不能为空");
        }

        Map<String, Object> result = copilotAgentService.chat(agentId, message, sessionId);
        return ApiResponse.success(result);
    }

    @GetMapping("/quick-questions")
    public ApiResponse<List<Map<String, Object>>> quickQuestions(@RequestParam String agentId) {
        List<Map<String, Object>> questions = copilotAgentService.getQuickQuestions(agentId);
        return ApiResponse.success(questions);
    }
}
