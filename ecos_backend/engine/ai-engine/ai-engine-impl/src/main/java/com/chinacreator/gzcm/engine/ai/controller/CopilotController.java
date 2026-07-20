package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.service.ICopilotAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agent/copilot")
public class CopilotController {

    private static final Logger log = LoggerFactory.getLogger(CopilotController.class);

    private final ICopilotAgentService copilotAgentService;

    public CopilotController(ICopilotAgentService copilotAgentService) {
        this.copilotAgentService = copilotAgentService;
    }

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> request) {
        try {
            String agentId = (String) request.getOrDefault("agentId", "agent-data");
            String message = (String) request.get("message");
            String sessionId = (String) request.getOrDefault("sessionId", UUID.randomUUID().toString());

            if (message == null || message.isBlank()) {
                return ApiResponse.badRequest("message 不能为空");
            }

            Map<String, Object> result = copilotAgentService.chat(agentId, message, sessionId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Copilot chat 失败", e);
            return ApiResponse.internalError("Copilot chat 失败: " + e.getMessage());
        }
    }

    @GetMapping("/quick-questions")
    public ApiResponse<List<Map<String, Object>>> quickQuestions(@RequestParam String agentId) {
        try {
            List<Map<String, Object>> questions = copilotAgentService.getQuickQuestions(agentId);
            return ApiResponse.success(questions);
        } catch (Exception e) {
            log.error("获取快捷问题失败: agentId={}", agentId, e);
            return ApiResponse.internalError("获取快捷问题失败: " + e.getMessage());
        }
    }
}
