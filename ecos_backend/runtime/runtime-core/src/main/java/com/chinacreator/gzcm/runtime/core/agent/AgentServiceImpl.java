package com.chinacreator.gzcm.runtime.core.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.runtime.core.agent.exception.AgentException;
import com.chinacreator.gzcm.runtime.core.agent.llm.LLMConfig;

/**
 * Agent 服务实现
 * 
 * 封装 AgentRuntime，提供高层服务 API。
 * 支持会话管理、一次性提示执行、流式回调等功能。
 *
 * @author CDRC Design Team
 */
public class AgentServiceImpl implements AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentServiceImpl.class);

    private final AgentRuntime runtime;
    private final Map<String, AgentProfile> sessionProfiles;

    public AgentServiceImpl(AgentRuntime runtime) {
        this.runtime = runtime;
        this.sessionProfiles = new ConcurrentHashMap<>();
    }

    // ── Chat Session Management ──────────────────

    @Override
    public String createChat(String systemPrompt) {
        return createChat(null, systemPrompt);
    }

    @Override
    public String createChat(String title, String systemPrompt) {
        AgentSession session = runtime.createSession(title, systemPrompt);
        log.info("Created agent session: id={}, title={}", session.getId(), session.getTitle());
        return session.getId();
    }

    @Override
    public AgentResult continueChat(String sessionId, String message) {
        try {
            log.debug("Continue chat: sessionId={}, message={}", sessionId,
                    message.length() > 100 ? message.substring(0, 100) + "..." : message);
            return runtime.run(sessionId, message);
        } catch (AgentException e) {
            log.error("Agent chat error: sessionId={}, error={}", sessionId, e.getMessage());
            return AgentResult.error(sessionId, e.getMessage());
        }
    }

    @Override
    public AgentResult continueChatAsync(String sessionId, String message, Consumer<String> callback) {
        try {
            log.debug("Continue chat async: sessionId={}, message={}", sessionId,
                    message.length() > 100 ? message.substring(0, 100) + "..." : message);
            return runtime.runAsync(sessionId, message, callback);
        } catch (AgentException e) {
            log.error("Agent chat async error: sessionId={}, error={}", sessionId, e.getMessage());
            return AgentResult.error(sessionId, e.getMessage());
        }
    }

    // ── One-shot Execution ───────────────────────

    @Override
    public AgentResult executePrompt(String prompt) {
        return executePrompt(prompt, "你是一个乐于助人的AI助手。");
    }

    @Override
    public AgentResult executePrompt(String prompt, String systemPrompt) {
        String sessionId = createChat(systemPrompt);
        try {
            AgentResult result = runtime.run(sessionId, prompt);
            // 一次性执行完成后自动关闭会话
            runtime.closeSession(sessionId);
            return result;
        } catch (AgentException e) {
            runtime.closeSession(sessionId);
            log.error("One-shot prompt error: {}", e.getMessage());
            return AgentResult.error(sessionId, e.getMessage());
        }
    }

    // ── Session Listing ──────────────────────────

    @Override
    public List<AgentSessionInfo> listSessions() {
        List<AgentSessionInfo> sessions = new ArrayList<>();
        Map<String, Object> stats = runtime.getGlobalStats();
        // AgentRuntime 没有直接暴露所有 session 的列表，
        // 我们通过 sessionProfiles 维护一份引用
        // 同时尝试通过已知的 profile 查找会话
        for (String sessionId : sessionProfiles.keySet()) {
            try {
                AgentSession session = runtime.getSession(sessionId);
                sessions.add(AgentSessionInfo.from(session));
            } catch (AgentException e) {
                // 会话已过期或不存在，清理 profile
                sessionProfiles.remove(sessionId);
            }
        }
        return sessions;
    }

    @Override
    public AgentSessionInfo getSessionInfo(String sessionId) {
        try {
            AgentSession session = runtime.getSession(sessionId);
            return AgentSessionInfo.from(session);
        } catch (AgentException e) {
            return null;
        }
    }

    // ── Session Management ───────────────────────

    @Override
    public void closeSession(String sessionId) {
        runtime.closeSession(sessionId);
        sessionProfiles.remove(sessionId);
        log.info("Closed agent session: {}", sessionId);
    }

    @Override
    public void closeAllSessions() {
        sessionProfiles.clear();
        runtime.shutdown();
        log.info("Closed all agent sessions");
    }

    // ── Status ───────────────────────────────────

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "AgentService");
        status.put("status", "running");

        // 从运行时获取统计信息
        Map<String, Object> stats = runtime.getGlobalStats();
        status.putAll(stats);

        // 附加信息
        LLMConfig config = runtime.getLLMConfig();
        status.put("model", config.getModel());
        status.put("provider", config.getProvider() != null ? config.getProvider().name() : "unknown");
        status.put("baseUrl", config.getBaseUrl());
        status.put("temperature", config.getTemperature());
        status.put("maxTokens", config.getMaxTokens());

        return status;
    }

    // ── Shutdown ────────────────────────────────

    @Override
    public void shutdown() {
        closeAllSessions();
        log.info("AgentService shutdown complete");
    }
}
