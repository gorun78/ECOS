package com.chinacreator.gzcm.engine.ai.copilot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.ai.service.AgentLoopConfig;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopResult;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopService;
import com.chinacreator.gzcm.runtime.llm.session.AgentSession;

/**
 * Copilot 对话执行器：把 6 个内置智能体接到真实 agent-loop（原生工具调用）。
 *
 * <p>每个智能体 = 目录里的系统提示词 + 引擎工具白名单（{@link CopilotAgentCatalog}）。
 * agent-loop 按白名单只下发该智能体可见的工具（见 {@code AgentLoopService#nativeToolSchemas}），
 * 模型返回 tool_calls 后由统一工具咽喉执行（含安全策略门），结果回灌后继续推理。</p>
 *
 * <p>失败语义：AgentLoop 不可用或结果失败时返回 {@code success=false} 与分类错误码，
 * 由 Controller 决定是否回落到旧版 Copilot 通道；本类不合成任何"成功"文案。</p>
 */
@Component
public class CopilotChatService {

    private static final Logger log = LoggerFactory.getLogger(CopilotChatService.class);

    private final CopilotAgentCatalog catalog;

    @Autowired(required = false)
    private AgentLoopService agentLoopService;

    public CopilotChatService(CopilotAgentCatalog catalog) {
        this.catalog = catalog;
    }

    /** 是否为目录内的内置智能体。 */
    public boolean supports(String agentId) {
        return catalog.find(agentId) != null;
    }

    /** 执行一次 Copilot 对话；AgentLoop 未装配时返回 {@code null}（调用方回落旧通道）。 */
    public Map<String, Object> chat(String agentId, String message, String sessionId) {
        CopilotAgentCatalog.Definition def = catalog.find(agentId);
        if (def == null) {
            return null;
        }
        if (agentLoopService == null) {
            log.warn("[Copilot] AgentLoopService 未装配，无法执行智能体 {}", agentId);
            return null;
        }

        AgentLoopConfig config = new AgentLoopConfig();
        config.setSystemPrompt(def.systemPrompt());
        config.setToolWhitelist(def.tools());
        config.setMaxIterations(4);
        config.setTemperature(0.3);

        AgentSession session = new AgentSession();
        session.setSessionId(sessionId);
        session.setSubsystem("ai-engine");
        session.setProfileName(agentId);
        session.setSystemPrompt(def.systemPrompt());
        session.setStatus("active");

        AgentLoopResult result = agentLoopService.run(config, message, session);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("agentId", def.id());
        out.put("agentName", def.name());
        out.put("engine", def.engine());
        out.put("sessionId", sessionId);
        out.put("tools", def.tools());
        if (result == null) {
            out.put("success", false);
            out.put("answer", "");
            out.put("errorCode", "ECOS-AI-500");
            return out;
        }
        out.put("success", result.isSuccess());
        out.put("answer", result.getContent() == null ? "" : result.getContent());
        out.put("turns", result.getTurns());
        out.put("totalTokens", result.getTotalTokens());
        if (result.getTraceId() != null) {
            out.put("traceId", result.getTraceId());
        }
        out.put("toolCalls", result.getToolCalls() == null ? List.of() : result.getToolCalls());
        if (!result.isSuccess()) {
            out.put("errorCode", result.getErrorCode());
            out.put("errorClass", result.getErrorClass());
        }
        log.info("[Copilot] agent={} success={} turns={} tools={}",
                def.id(), result.isSuccess(), result.getTurns(),
                result.getToolCalls() == null ? 0 : result.getToolCalls().size());
        return out;
    }
}
