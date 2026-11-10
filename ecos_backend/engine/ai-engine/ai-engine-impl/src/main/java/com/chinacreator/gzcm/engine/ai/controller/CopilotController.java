package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.service.ICopilotAgentService;
import com.chinacreator.gzcm.engine.ai.copilot.CopilotAgentCatalog;
import com.chinacreator.gzcm.engine.ai.copilot.CopilotChatService;
import com.chinacreator.gzcm.engine.ai.copilot.CopilotToolCatalog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Copilot Controller — 6 个内置智能体目录与真实工具调用对话。
 *
 * <p>路径与既有签名零变化（API 只增不改）：原有 {@code /chat}、{@code /quick-questions}
 * 保持可用；新增 {@code GET /agents}（内置智能体目录）与 {@code GET /tools}
 * （六域全生命周期工具清单，工作台「工具面」管理消费）。</p>
 *
 * <p>对话路由：目录内智能体 → {@link CopilotChatService}（agent-loop + 引擎工具白名单，真实执行）；
 * 非目录智能体或 AgentLoop 未装配 → 回落既有 {@link ICopilotAgentService} 通道（保持兼容）。</p>
 */
@RestController
@RequestMapping("/api/v1/agent/copilot")
public class CopilotController {

    private final ICopilotAgentService copilotAgentService;

    @Autowired(required = false)
    private CopilotChatService copilotChatService;

    @Autowired(required = false)
    private CopilotAgentCatalog agentCatalog;

    public CopilotController(ICopilotAgentService copilotAgentService) {
        this.copilotAgentService = copilotAgentService;
    }

    /** GET /agents — 内置智能体目录（6 个；不含系统提示词，提示词只留服务端）。 */
    @GetMapping("/agents")
    public ApiResponse<List<Map<String, Object>>> agents() {
        if (agentCatalog == null) {
            return ApiResponse.error(501, "ECOS-AI-AGENT-RUNTIME-ABSENT",
                    "Copilot 智能体目录未装配");
        }
        List<Map<String, Object>> wire = new ArrayList<>();
        agentCatalog.all().forEach(def -> wire.add(agentCatalog.toWire(def, true)));
        return ApiResponse.success(wire);
    }

    /** GET /tools — 六域全生命周期工具清单（可选 agentId 过滤；工作台工具面管理消费）。 */
    @GetMapping("/tools")
    public ApiResponse<List<Map<String, Object>>> tools(
            @RequestParam(value = "agentId", required = false) String agentId) {
        List<CopilotToolCatalog.Tool> tools = agentId == null || agentId.isBlank()
                ? CopilotToolCatalog.all()
                : CopilotToolCatalog.forAgent(agentId);
        List<Map<String, Object>> wire = new ArrayList<>();
        for (CopilotToolCatalog.Tool tool : tools) {
            wire.add(CopilotToolCatalog.toWire(tool));
        }
        return ApiResponse.success(wire);
    }

    /** POST /chat — 目录内智能体走真实 agent-loop（工具调用），否则回落旧通道。 */
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> request) {
        String agentId = (String) request.getOrDefault("agentId", "agent-data");
        String message = (String) request.get("message");
        String sessionId = (String) request.getOrDefault("sessionId", UUID.randomUUID().toString());

        if (message == null || message.isBlank()) {
            return ApiResponse.badRequest("message 不能为空");
        }

        if (copilotChatService != null && copilotChatService.supports(agentId)) {
            Map<String, Object> result = copilotChatService.chat(agentId, message, sessionId);
            if (result != null) {
                return ApiResponse.success(result);
            }
        }

        Map<String, Object> legacy = copilotAgentService.chat(agentId, message, sessionId);
        return ApiResponse.success(legacy);
    }

    /** GET /quick-questions — 目录内智能体返回目录建议问题；否则回落旧通道。 */
    @GetMapping("/quick-questions")
    public ApiResponse<List<Map<String, Object>>> quickQuestions(@RequestParam String agentId) {
        if (agentCatalog != null) {
            CopilotAgentCatalog.Definition def = agentCatalog.find(agentId);
            if (def != null) {
                List<Map<String, Object>> questions = new ArrayList<>();
                for (String q : def.quickQuestions()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("question", q);
                    item.put("agentId", def.id());
                    questions.add(item);
                }
                return ApiResponse.success(questions);
            }
        }
        List<Map<String, Object>> questions = copilotAgentService.getQuickQuestions(agentId);
        return ApiResponse.success(questions);
    }
}
