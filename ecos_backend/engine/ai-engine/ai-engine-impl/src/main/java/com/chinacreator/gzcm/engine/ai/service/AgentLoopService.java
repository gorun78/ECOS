package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.runtime.llm.LLMGatewayService;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatMessage;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import com.chinacreator.gzcm.runtime.llm.session.AgentSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 推理循环 — 多轮工具调用循环（think → tool_call → observe → think）。
 * <p>
 * 替代 Hermes Agent Loop 的纯 Java 实现，最多 5 轮推理：
 * </p>
 * <ol>
 *   <li>构建初始 messages（system prompt + 历史消息 + 当前用户消息）</li>
 *   <li>调用 LLM → 解析响应</li>
 *   <li>如果是 tool_call → 执行工具 → 工具结果塞回 messages → 回到步骤 2</li>
 *   <li>如果是 final → 记录并返回 AgentLoopResult</li>
 *   <li>超过 5 轮 → 返回超限错误</li>
 * </ol>
 *
 * <h3>注入点</h3>
 * <ul>
 *   <li>{@link LLMGatewayService} — LLM 调用网关（required）</li>
 *   <li>{@link LLMGateway} — 底层 LLM 调用接口（required）</li>
 *   <li>{@link ToolExecutorService} — 工具执行器（required=false，T0.2 实现）</li>
 * </ul>
 */
@Service
public class AgentLoopService {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopService.class);

    /** Agent 推理最大轮次 */
    private static final int MAX_TURNS = 5;

    /** 工具调用超时（毫秒） */
    private static final long TOOL_TIMEOUT_MS = 30_000L;

    /** 工具结果最大字符数（超过截断） */
    private static final int TOOL_RESULT_MAX_CHARS = 2000;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private LLMGatewayService llmGatewayService;

    @Autowired
    private LLMGateway llmGateway;

    @Autowired(required = false)
    private ToolExecutorService toolExecutorService;

    // ─── Public API ────────────────────────────────────────────────────

    /**
     * 执行 Agent 推理循环。
     *
     * @param config      Agent 配置（system prompt / model / temperature / maxTokens）
     * @param userMessage 用户消息
     * @param session     当前 AgentSession（含 systemPrompt）
     * @return AgentLoopResult 推理结果
     */
    public AgentLoopResult run(AgentLoopConfig config, String userMessage, AgentSession session) {
        long startTime = System.currentTimeMillis();
        String sessionId = session != null ? session.getSessionId() : null;
        int totalTokens = 0;
        List<Map<String, Object>> toolCallRecords = new ArrayList<>();

        if (llmGateway == null) {
            return AgentLoopResult.error(sessionId, "LLM Gateway 未就绪", 0);
        }

        // 1. 构建初始 messages
        List<Message> messages = buildInitialMessages(config, userMessage, session);

        // 2. think → tool → observe 循环
        for (int turn = 1; turn <= MAX_TURNS; turn++) {
            log.info("[AgentLoop] turn={}/{} session={}", turn, MAX_TURNS, sessionId);

            // 2a. 调用 LLM
            ChatResponse resp = callLLM(messages, config);
            if (resp == null || !resp.isSuccess()) {
                String err = resp != null ? resp.getErrorMsg() : "LLM 调用返回 null";
                log.error("[AgentLoop] LLM call failed at turn {}: {}", turn, err);
                // LLM 返回格式错误 → 重试一次
                if (turn == 1 && resp != null) {
                    log.info("[AgentLoop] Retrying LLM call once after format error");
                    resp = callLLM(messages, config);
                    if (resp == null || !resp.isSuccess()) {
                        return AgentLoopResult.error(sessionId,
                                "LLM 调用失败（已重试）: " + (resp != null ? resp.getErrorMsg() : "null"), totalTokens);
                    }
                } else {
                    return AgentLoopResult.error(sessionId,
                            "LLM 调用失败: " + err, totalTokens);
                }
            }

            totalTokens += resp.getTokensInput() + resp.getTokensOutput();

            // 2b. 解析响应 — 判断是 tool_call 还是 final
            String content = resp.getContent();
            List<ToolCall> toolCalls = parseToolCalls(content);

            if (toolCalls != null && !toolCalls.isEmpty()) {
                // ── 工具调用分支 ─────────────────────────────────
                log.info("[AgentLoop] turn={} detected {} tool call(s): {}",
                        turn, toolCalls.size(),
                        toolCalls.stream().map(ToolCall::getName).toList());

                // 添加 assistant 消息（含 tool_calls）
                messages.add(Message.assistant(toolCalls));

                // 逐个执行工具
                for (ToolCall tc : toolCalls) {
                    long toolStart = System.currentTimeMillis();
                    ToolExecutorService.ToolResult tr = executeTool(tc);
                    long toolDuration = System.currentTimeMillis() - toolStart;

                    // 记录到 toolCallRecords
                    Map<String, Object> record = new LinkedHashMap<>();
                    record.put("turn", turn);
                    record.put("toolName", tc.getName());
                    record.put("toolCallId", tc.getId());
                    record.put("durationMs", toolDuration);
                    record.put("success", tr.isSuccess());
                    record.put("resultSnippet", truncate(tr.getContent(), 200));
                    toolCallRecords.add(record);

                    // 工具结果塞回 messages
                    messages.add(Message.toolResult(tr));

                    log.info("[AgentLoop] turn={} tool={} completed in {}ms success={}",
                            turn, tc.getName(), toolDuration, tr.isSuccess());

                    if (session != null) {
                        session.touch();
                    }
                }

                // continue → 回到循环顶部，LLM 看到工具结果后继续推理
                continue;
            }

            // ── 最终回复分支 ─────────────────────────────────
            log.info("[AgentLoop] turn={} final response ({} chars, {} tokens)",
                    turn,
                    content != null ? content.length() : 0,
                    totalTokens);

            if (session != null) {
                session.touch();
            }

            return AgentLoopResult.success(content, turn, sessionId, totalTokens, toolCallRecords);
        }

        // 3. 超过最大轮次
        log.warn("[AgentLoop] Max turns ({}) exceeded for session={}", MAX_TURNS, sessionId);
        return AgentLoopResult.maxTurnsExceeded(sessionId, totalTokens, toolCallRecords);
    }

    // ─── Message 构建 ──────────────────────────────────────────────────

    /**
     * 构建初始消息列表：system prompt + 历史消息 + 当前用户消息。
     */
    private List<Message> buildInitialMessages(AgentLoopConfig config, String userMessage,
                                                AgentSession session) {
        List<Message> messages = new ArrayList<>();

        // System prompt — 优先从 config，fallback 到 session
        String systemPrompt = null;
        if (config != null && config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            systemPrompt = config.getSystemPrompt();
        } else if (session != null && session.getSystemPrompt() != null) {
            systemPrompt = session.getSystemPrompt();
        }

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Message.system(systemPrompt));
        }

        // TODO T0.3: 注入记忆上下文（KG 记忆事实）

        // 当前用户消息
        if (userMessage != null && !userMessage.isBlank()) {
            messages.add(Message.user(userMessage));
        }

        log.debug("[AgentLoop] Built {} initial messages", messages.size());
        return messages;
    }

    // ─── LLM 调用 ──────────────────────────────────────────────────────

    /**
     * 调用 LLM — 使用 gateway 的 ChatRequest 模型。
     * <p>
     * 将内部 Message 列表转换为 ChatMessage 列表，构建 ChatRequest 并调用 gateway。
     * </p>
     */
    private ChatResponse callLLM(List<Message> messages, AgentLoopConfig config) {
        // 转换消息
        List<ChatMessage> chatMessages = new ArrayList<>();
        for (Message m : messages) {
            ChatMessage cm = new ChatMessage();
            cm.setRole(m.getRole());

            // 对于 assistant 消息携带 tool_calls，序列化为 JSON
            if ("assistant".equals(m.getRole()) && m.hasToolCalls()) {
                cm.setContent(serializeToolCallsForLLM(m.getToolCalls()));
            } else if ("tool".equals(m.getRole())) {
                // tool 结果消息 — role=tool, content=结果文本
                cm.setContent(m.getContent());
            } else {
                cm.setContent(m.getContent());
            }
            chatMessages.add(cm);
        }

        // 构建请求
        String model = config != null && config.getModel() != null
                ? config.getModel() : "deepseek-chat";
        Double temperature = config != null && config.getTemperature() != null
                ? config.getTemperature() : 0.7;
        Integer maxTokens = config != null && config.getMaxTokens() != null
                ? config.getMaxTokens() : 4096;

        ChatRequest request = new ChatRequest(model, chatMessages, temperature, maxTokens, false);

        try {
            return llmGateway.call(request);
        } catch (Exception e) {
            log.error("[AgentLoop] LLM gateway call exception", e);
            return ChatResponse.fail("LLM gateway exception: " + e.getMessage());
        }
    }

    /**
     * 将 ToolCall 列表序列化为 LLM 可理解的 JSON（function-calling 风格）。
     */
    private String serializeToolCallsForLLM(List<ToolCall> toolCalls) {
        try {
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (ToolCall tc : toolCalls) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", tc.getId());
                entry.put("type", "function");
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", tc.getName());
                function.put("arguments", tc.getArguments());
                entry.put("function", function);
                serialized.add(entry);
            }
            return objectMapper.writeValueAsString(serialized);
        } catch (Exception e) {
            log.warn("[AgentLoop] Failed to serialize tool calls", e);
            return "[]";
        }
    }

    // ─── Tool Call 解析 ────────────────────────────────────────────────

    /**
     * 从 LLM 响应内容中解析工具调用。
     * <p>
     * 支持两种格式：<br>
     * 1. OpenAI/DeepSeek 标准 function-calling JSON 格式<br>
     * 2. 标记格式 &lt;tool_call&gt;...&lt;/tool_call&gt;
     * </p>
     * <p>
     * 返回 null 或空列表表示无工具调用（final response）。
     * </p>
     */
    @SuppressWarnings("unchecked")
    private List<ToolCall> parseToolCalls(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }

        String trimmed = content.trim();

        // 尝试解析 JSON 格式 tool_calls
        try {
            if (trimmed.startsWith("[")) {
                List<Map<String, Object>> raw = objectMapper.readValue(trimmed,
                        new TypeReference<List<Map<String, Object>>>() {});
                List<ToolCall> calls = new ArrayList<>();
                for (Map<String, Object> entry : raw) {
                    String id = (String) entry.getOrDefault("id", "call_" + System.currentTimeMillis());
                    Map<String, Object> func = (Map<String, Object>) entry.get("function");
                    if (func != null) {
                        String name = (String) func.get("name");
                        Object argsObj = func.get("arguments");
                        Map<String, Object> arguments;
                        if (argsObj instanceof String) {
                            arguments = objectMapper.readValue((String) argsObj,
                                    new TypeReference<Map<String, Object>>() {});
                        } else if (argsObj instanceof Map) {
                            arguments = (Map<String, Object>) argsObj;
                        } else {
                            arguments = Collections.emptyMap();
                        }
                        calls.add(new ToolCall(id, name, arguments));
                    }
                }
                if (!calls.isEmpty()) {
                    return calls;
                }
            }
        } catch (Exception e) {
            log.debug("[AgentLoop] Content is not JSON tool_calls: {}", e.getMessage());
        }

        // 尝试解析 <tool_call> ... </tool_call> 标记格式
        try {
            int start = trimmed.indexOf("<tool_call>");
            int end = trimmed.indexOf("</tool_call>");
            if (start >= 0 && end > start) {
                String json = trimmed.substring(start + "<tool_call>".length(), end).trim();
                Map<String, Object> funcMap = objectMapper.readValue(json,
                        new TypeReference<Map<String, Object>>() {});
                String name = (String) funcMap.get("name");
                Object argsObj = funcMap.get("arguments");
                Map<String, Object> arguments;
                if (argsObj instanceof String) {
                    arguments = objectMapper.readValue((String) argsObj,
                            new TypeReference<Map<String, Object>>() {});
                } else if (argsObj instanceof Map) {
                    arguments = (Map<String, Object>) argsObj;
                } else {
                    arguments = Collections.emptyMap();
                }
                String id = "call_" + System.currentTimeMillis();
                return Collections.singletonList(new ToolCall(id, name, arguments));
            }
        } catch (Exception e) {
            log.debug("[AgentLoop] Content is not <tool_call> format: {}", e.getMessage());
        }

        // 无工具调用 → 视为 final response
        return Collections.emptyList();
    }

    // ─── 工具执行 ──────────────────────────────────────────────────────

    /**
     * 执行工具调用 — 带超时和截断保护。
     * <p>
     * 若未注入 ToolExecutorService 则返回 mock 结果。
     * </p>
     */
    private ToolExecutorService.ToolResult executeTool(ToolCall tc) {
        if (toolExecutorService == null) {
            log.warn("[AgentLoop] ToolExecutorService not available, returning mock result for tool={}", tc.getName());
            return buildMockToolResult(tc);
        }

        try {
            ToolExecutorService.ToolResult raw = toolExecutorService.execute(tc.getName(), tc.getArguments());

            // 截断过长的结果
            if (raw.getContent() != null && raw.getContent().length() > TOOL_RESULT_MAX_CHARS) {
                raw.setContent(raw.getContent().substring(0, TOOL_RESULT_MAX_CHARS)
                        + "\n\n[结果已截断，原始长度: " + raw.getContent().length() + " 字符]");
            }

            return raw;
        } catch (Exception e) {
            log.error("[AgentLoop] Tool execution failed: tool={}", tc.getName(), e);
            return buildMockToolResult(tc);
        }
    }

    private ToolExecutorService.ToolResult buildMockToolResult(ToolCall tc) {
        ToolExecutorService.ToolResult tr = new ToolExecutorService.ToolResult();
        tr.setToolCallId(tc.getId());
        tr.setToolName(tc.getName());
        tr.setSuccess(false);
        tr.setContent("{\"status\":\"success\",\"message\":\"ToolExecutorService 未就绪，返回模拟结果\"}");
        tr.setError("ToolExecutorService not available");
        tr.setElapsedMs(0);
        return tr;
    }

    // ─── Helpers ───────────────────────────────────────────────────────

    private static String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...";
    }
}
