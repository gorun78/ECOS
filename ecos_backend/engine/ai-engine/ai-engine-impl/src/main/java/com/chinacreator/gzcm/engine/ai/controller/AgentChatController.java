package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.services.agent.runtime.memory.MemoryService;
import com.chinacreator.gzcm.services.agent.runtime.model.*;
import com.chinacreator.gzcm.services.agent.runtime.planner.PlannerService;
import com.chinacreator.gzcm.services.agent.runtime.executor.ExecutorService;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolRouterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentChatController {

    private static final Logger log = LoggerFactory.getLogger(AgentChatController.class);

    @Autowired
    private ToolRouterService toolRouterService;

    @Autowired
    private PlannerService plannerService;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private MemoryService memoryService;

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> request) {
        try {
            String agentId = (String) request.getOrDefault("agentId", "default");
            String message = (String) request.get("message");
            String sessionId = (String) request.getOrDefault("sessionId", UUID.randomUUID().toString());

            if (message == null || message.trim().isEmpty()) {
                return ApiResponse.badRequest("message 不能为空");
            }

            MemoryContext ctx = memoryService.buildContext(agentId, sessionId);

            Goal goal = new Goal();
            goal.setId(UUID.randomUUID().toString());
            goal.setDescription(message);
            ExecutionPlan plan = plannerService.createPlan(goal);

            ExecutionTask task = new ExecutionTask();
            task.setId(UUID.randomUUID().toString());
            task.setInstruction(message);
            task.setAgentId(agentId);
            task.setSessionId(sessionId);
            ExecutionResult result = executorService.execute(task);

            MemoryRecord record = new MemoryRecord();
            record.setAgentId(agentId);
            record.setSessionId(sessionId);
            record.setLayer(MemoryLayer.WORKING);
            record.setContent(message + " -> " + result.getOutput());
            memoryService.store(record);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("answer", result.getOutput());
            resp.put("thoughtChain", List.of("Received: " + message, "Planned execution", "Executed"));
            resp.put("toolCalls", List.of());
            resp.put("sources", List.of());
            resp.put("sessionId", sessionId);

            return ApiResponse.success(resp);
        } catch (Exception e) {
            log.error("Agent chat 执行失败", e);
            return ApiResponse.internalError("Agent 执行失败: " + e.getMessage());
        }
    }
}
