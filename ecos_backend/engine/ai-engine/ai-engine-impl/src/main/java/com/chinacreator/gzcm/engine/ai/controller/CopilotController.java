package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.copilot.CopilotAgentCatalog;
import com.chinacreator.gzcm.engine.ai.copilot.CopilotChatService;
import com.chinacreator.gzcm.engine.ai.copilot.CopilotToolCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
 * 目录外 agentId → 400 明确拒绝（D18：不再回落会返回占位串的旧通道）。</p>
 */
@RestController
@RequestMapping("/api/v1/agent/copilot")
public class CopilotController {

    private static final Logger log = LoggerFactory.getLogger(CopilotController.class);

    @Autowired(required = false)
    private CopilotChatService copilotChatService;

    @Autowired(required = false)
    private CopilotAgentCatalog agentCatalog;

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

    /**
     * POST /chat — 目录内智能体走真实 agent-loop（工具调用）；目录外 agentId 明确拒绝。
     *
     * <p><b>D63①（2026-11-14 验证，HTTP 状态行保真族）</b>：原实现无论 agent-loop 成功还是
     * 护栏 DENY，一律 {@code ApiResponse.success(...)} ⇒ 传输层恒 200，403/503 语义只藏在
     * body 的 {@code errorCode} 里（curl 与浏览器看到两个不同的"成败"）。
     * 现按信封 {@code code} 一族规则补状态行：<b>信封逐字段不动</b>、路径/入参零改动，
     * 只在 {@code result.success=false} 时把 {@code errorCode} 经 {@link AiErrorClass}
     * 单源查表抬成对应的 HTTP 状态（查不到族码 → 500，不猜）。</p>
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<Map<String, Object>>> chat(@RequestBody Map<String, Object> request) {
        String agentId = (String) request.getOrDefault("agentId", "agent-data");
        String message = (String) request.get("message");
        String sessionId = (String) request.getOrDefault("sessionId", UUID.randomUUID().toString());

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.badRequest("message 不能为空"));
        }

        if (copilotChatService != null && copilotChatService.supports(agentId)) {
            Map<String, Object> result = copilotChatService.chat(agentId, message, sessionId);
            if (result != null) {
                return withStatusForEnvelope(ApiResponse.success(result), result);
            }
        }

        // D18（2026-11-13 验证）：原实现对目录外 agentId 回落 CopilotAgentServiceImpl「旧通道」，
        // 而该通道的 answer 来自 ExecutorServiceImpl 的占位串（"Task completed: …"）、
        // thoughtChain 是写死的 ["Received: …","Planned","Executed"]、toolCalls 恒空、
        // 且原样回显传入的 sessionId ⇒ 未知 Agent 会拿到一段"看起来像 AI 回答"的伪造结果。
        // 现改为明确拒绝，让调用方看到真实原因（目录内的 agent 不受影响）。
        log.warn("[Copilot] 未注册的 agentId={}（目录内 Agent 见 GET /api/v1/agent/copilot/agents）", agentId);
        return ResponseEntity.badRequest().body(ApiResponse.badRequest(
            "未注册的 Copilot Agent: " + agentId + "（可选值见 GET /api/v1/agent/copilot/agents）"));
    }

    /**
     * 按 agent-loop 结果补 HTTP 状态行（信封不动）。
     *
     * <p>成功 ⇒ 200；失败 ⇒ 按 {@code errorCode} 在 {@link AiErrorClass} 冻结表里查 httpStatus。
     * 族码查不到（含 {@code ECOS-AI-500} 这类非枚举码位）⇒ 500 —— 宁可报服务端错误，
     * 也不把失败压成 200。</p>
     */
    private static ResponseEntity<ApiResponse<Map<String, Object>>> withStatusForEnvelope(
            ApiResponse<Map<String, Object>> body, Map<String, Object> result) {
        Object success = result.get("success");
        if (!Boolean.FALSE.equals(success)) {
            return ResponseEntity.ok(body);
        }
        Object code = result.get("errorCode");
        int status = httpStatusOf(code == null ? null : code.toString());
        return ResponseEntity.status(status).body(body);
    }

    /** 族码 → HTTP 状态（单源 = {@link AiErrorClass#httpStatus()}）；未登记码位一律 500。 */
    private static int httpStatusOf(String errorCode) {
        if (errorCode != null) {
            for (AiErrorClass c : AiErrorClass.values()) {
                if (c.errorCode().equals(errorCode)) {
                    return c.httpStatus();
                }
            }
        }
        return 500;
    }

    /** GET /quick-questions — 目录内智能体返回目录建议问题；目录外 agentId 明确拒绝。 */
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
        // D18 同源：旧通道对未注册 agentId 会 getOrDefault →「你好，有什么可以帮你？」，
        // 让调用方以为该 Agent 存在。改为与 /chat 同口径的明确拒绝。
        log.warn("[Copilot] quick-questions 收到未注册的 agentId={}", agentId);
        return ApiResponse.badRequest(
            "未注册的 Copilot Agent: " + agentId + "（可选值见 GET /api/v1/agent/copilot/agents）");
    }
}
