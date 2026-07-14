package com.chinacreator.gzcm.runtime.core.agent.impl;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.chinacreator.gzcm.runtime.core.agent.AgentMessage;
import com.chinacreator.gzcm.runtime.core.agent.AgentResult;
import com.chinacreator.gzcm.runtime.core.agent.AgentRuntime;
import com.chinacreator.gzcm.runtime.core.agent.AgentSession;
import com.chinacreator.gzcm.runtime.core.agent.exception.AgentException;
import com.chinacreator.gzcm.runtime.core.agent.llm.ChatRequest;
import com.chinacreator.gzcm.runtime.core.agent.llm.ChatResponse;
import com.chinacreator.gzcm.runtime.core.agent.llm.LLMClient;
import com.chinacreator.gzcm.runtime.core.agent.llm.LLMConfig;
import com.chinacreator.gzcm.runtime.core.agent.tool.ToolCall;
import com.chinacreator.gzcm.runtime.core.agent.tool.ToolRegistry;
import com.chinacreator.gzcm.runtime.core.agent.tool.ToolResult;

/**
 * Agent 运行时实现
 * 
 * 核心执行引擎，实现了 Agent 的思考-行动-观察循环（ReAct 模式）：
 * 1. 接收用户消息
 * 2. 构建 LLM 请求（含工具定义）
 * 3. LLM 返回文本回复 或 工具调用指令
 * 4. 如果是工具调用 → 执行工具 → 将结果反馈给 LLM → 继续
 * 5. 如果是文本回复 → 返回最终结果
 *
 * @author CDRC Design Team
 */
public class AgentRuntimeImpl implements AgentRuntime {

    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final Map<String, AgentSession> sessions;
    private LLMConfig llmConfig;

    public AgentRuntimeImpl(LLMConfig config) {
        this.llmConfig = config;
        this.llmClient = new DefaultLLMClient(config);
        this.toolRegistry = new ToolRegistryImpl();
        this.sessions = new ConcurrentHashMap<>();
    }

    public AgentRuntimeImpl(LLMConfig config, LLMClient llmClient, ToolRegistry toolRegistry) {
        this.llmConfig = config;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.sessions = new ConcurrentHashMap<>();
    }

    // ── Session Management ───────────────────────

    @Override
    public AgentSession createSession(String systemPrompt) {
        return createSession(null, systemPrompt);
    }

    @Override
    public AgentSession createSession(String title, String systemPrompt) {
        String id = UUID.randomUUID().toString();
        AgentSessionImpl session = new AgentSessionImpl(id,
                title != null ? title : "Agent Session " + id.substring(0, 8), systemPrompt);
        sessions.put(id, session);
        return session;
    }

    @Override
    public AgentSession getSession(String sessionId) {
        AgentSession session = sessions.get(sessionId);
        if (session == null) {
            throw new AgentException(sessionId, AgentException.ERR_SESSION_NOT_FOUND,
                    "会话不存在: " + sessionId);
        }
        return session;
    }

    @Override
    public void closeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    // ── Agent Execution ──────────────────────────

    @Override
    public AgentResult run(String sessionId, String userMessage) {
        AgentSession session = getSession(sessionId);

        // 添加用户消息
        session.addMessage(AgentMessage.user(userMessage));
        session.setMetadata("lastUserMessage", userMessage);

        return executeLoop(session, null);
    }

    @Override
    public AgentResult runAsync(String sessionId, String userMessage, Consumer<String> callback) {
        AgentSession session = getSession(sessionId);
        session.addMessage(AgentMessage.user(userMessage));

        return executeLoop(session, callback);
    }

    @Override
    public AgentResult continueRun(String sessionId) {
        AgentSession session = getSession(sessionId);
        return executeLoop(session, null);
    }

    /**
     * Agent 主执行循环（ReAct 模式）
     */
    private AgentResult executeLoop(AgentSession session, Consumer<String> callback) {
        long startTime = System.currentTimeMillis();
        int totalTokens = 0;
        int toolCalls = 0;

        try {
            while (session.getCurrentIteration() < session.getMaxIterations()) {
                session.incrementIteration();

                // 1. 构建 LLM 请求
                ChatRequest request = buildChatRequest(session);

                // 2. 调用 LLM
                ChatResponse response;
                if (callback != null) {
                    response = llmClient.chatStream(request, callback::accept);
                } else {
                    response = llmClient.chat(request);
                }

                // 3. 累加 Token
                if (response.getUsage() != null) {
                    totalTokens += response.getUsage().getTotalTokens();
                    session.addTokens(response.getUsage().getTotalTokens());
                }

                // 4. 检查是否需要工具调用
                if (response.hasToolCalls()) {
                    toolCalls += handleToolCalls(session, response);
                    continue; // 继续下一轮迭代
                }

                // 5. 文本回复 → 最终答案
                String answer = response.getContent();
                session.addMessage(AgentMessage.assistant(answer));
                session.complete();

                long duration = System.currentTimeMillis() - startTime;
                return AgentResult.success(session.getId(), answer, totalTokens, toolCalls, duration);
            }

            // 达到最大迭代次数
            throw new AgentException(session.getId(), AgentException.ERR_MAX_ITERATIONS,
                    "Agent 超出最大迭代次数: " + session.getMaxIterations());

        } catch (AgentException e) {
            long duration = System.currentTimeMillis() - startTime;
            return AgentResult.error(session.getId(), e.getMessage());
        }
    }

    /**
     * 处理工具调用
     */
    private int handleToolCalls(AgentSession session, ChatResponse response) {
        // 记录助手消息（含工具调用）
        AgentMessage assistantMsg = AgentMessage.assistant(
                response.getContent() != null ? response.getContent() : "");
        session.addMessage(assistantMsg);

        int count = 0;
        for (ChatRequest.ToolCallRequest tcRequest : response.getToolCalls()) {
            session.incrementToolCallCount();
            count++;

            // 解析工具参数
            Map<String, Object> arguments = parseArguments(tcRequest.getFunction().getArguments());

            // 创建 ToolCall
            ToolCall toolCall = new ToolCall(tcRequest.getFunction().getName(), arguments);
            toolCall.setId(tcRequest.getId());
            toolCall.setSessionId(session.getId());

            // 执行工具
            ToolResult result = toolRegistry.executeTool(toolCall);

            // 将工具结果作为消息加入会话历史
            String resultContent = result.isSuccess() ? result.getContent() :
                    "Error: " + result.getErrorMessage();
            session.addMessage(AgentMessage.tool(
                    tcRequest.getId(),
                    tcRequest.getFunction().getName(),
                    resultContent));
        }

        return count;
    }

    /**
     * 构建 LLM 对话请求
     */
    private ChatRequest buildChatRequest(AgentSession session) {
        ChatRequest request = new ChatRequest();
        request.setModel(llmConfig.getModel());
        request.setTemperature(llmConfig.getTemperature());
        request.setMaxTokens(llmConfig.getMaxTokens());

        // 转换消息格式
        List<ChatRequest.ChatMessage> chatMessages = new ArrayList<>();
        for (AgentMessage msg : session.getHistory()) {
            chatMessages.add(convertMessage(msg));
        }
        request.setMessages(chatMessages);

        // 添加工具定义
        if (toolRegistry.getToolCount() > 0) {
            request.setTools(toolRegistry.getToolDefinitions());
            request.setToolChoice("auto");
        }

        return request;
    }

    private ChatRequest.ChatMessage convertMessage(AgentMessage msg) {
        switch (msg.getRole()) {
            case SYSTEM:
                return ChatRequest.ChatMessage.system(msg.getContent());
            case USER:
                return ChatRequest.ChatMessage.user(msg.getContent());
            case ASSISTANT:
                return ChatRequest.ChatMessage.assistant(msg.getContent());
            case TOOL:
                return ChatRequest.ChatMessage.tool(
                        msg.getToolCallId(), msg.getToolName(), msg.getContent());
            default:
                return new ChatRequest.ChatMessage(msg.getRole().name().toLowerCase(), msg.getContent());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String jsonArgs) {
        if (jsonArgs == null || jsonArgs.trim().isEmpty()) return new HashMap<>();
        // 简单 JSON → Map 解析（生产环境应用 Jackson）
        try {
            Map<String, Object> args = new LinkedHashMap<>();
            String json = jsonArgs.trim();
            if (json.startsWith("{") && json.endsWith("}")) {
                json = json.substring(1, json.length() - 1);
                String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                for (String pair : pairs) {
                    String[] kv = pair.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim().replaceAll("^\"|\"$", "");
                        String val = kv[1].trim();
                        if (val.startsWith("\"")) {
                            args.put(key, val.replaceAll("^\"|\"$", ""));
                        } else if (val.equals("true") || val.equals("false")) {
                            args.put(key, Boolean.parseBoolean(val));
                        } else if (val.equals("null")) {
                            args.put(key, null);
                        } else {
                            try {
                                args.put(key, Integer.parseInt(val));
                            } catch (NumberFormatException e1) {
                                try {
                                    args.put(key, Double.parseDouble(val));
                                } catch (NumberFormatException e2) {
                                    args.put(key, val);
                                }
                            }
                        }
                    }
                }
            }
            return args;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    // ── Configuration ────────────────────────────

    @Override
    public LLMClient getLLMClient() {
        return llmClient;
    }

    @Override
    public LLMConfig getLLMConfig() {
        return llmConfig;
    }

    @Override
    public void updateLLMConfig(LLMConfig config) {
        this.llmConfig = config;
    }

    // ── Tool Registry ────────────────────────────

    @Override
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    // ── Stats ────────────────────────────────────

    @Override
    public int getActiveSessionCount() {
        return (int) sessions.values().stream().filter(s -> !s.isCompleted()).count();
    }

    @Override
    public Map<String, Object> getGlobalStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("activeSessions", getActiveSessionCount());
        stats.put("totalSessions", sessions.size());
        stats.put("toolCount", toolRegistry.getToolCount());
        stats.put("model", llmConfig.getModel());
        stats.put("provider", llmConfig.getProvider().name());
        stats.put("llmStats", llmClient.getUsageStats());
        return stats;
    }

    @Override
    public void shutdown() {
        sessions.clear();
    }
}
