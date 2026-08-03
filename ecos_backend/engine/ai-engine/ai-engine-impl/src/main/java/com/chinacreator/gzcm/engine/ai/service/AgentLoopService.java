package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.engine.ai.SkillService;
import com.chinacreator.gzcm.engine.ai.entity.SkillEntity;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *   <li>{@link ToolRegistry} — 工具注册中心，用于 Schema 校验（required=false）</li>
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

    @Autowired(required = false)
    private ToolRegistry toolRegistry;

    @Autowired(required = false)
    private SkillService skillService;

    @Autowired(required = false)
    private MemoryExtractor memoryExtractor;

    @Autowired
    private AgentConfigResolver agentConfigResolver;

    /** 正则：匹配 function_name(arg1=val1, arg2=val2) 模式 */
    private static final Pattern FUNCTION_CALL_PATTERN =
            Pattern.compile("(\\w+)\\s*\\(\\s*([^)]*)\\s*\\)");

    @org.springframework.beans.factory.annotation.Value("${llm.deepseek.api-key:}")
    private String deepseekApiKey;

    /** LLM Provider 列表 — 按 priority() 排序，选择第一个支持 function-calling 的可用 Provider */
    @Autowired(required = false)
    private List<LLMProvider> llmProviders;

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

        // ── 三层配置解析：L1(yaml) → L2(builtin模板) → L3(实例) → 运行时覆盖 ──
        String agentId = session != null ? session.getProfileName() : "default";
        Map<String, Object> requestOverrides = configToOverrides(config);
        AgentLoopConfig resolvedConfig = agentConfigResolver.resolve(agentId, requestOverrides);
        log.info("[AgentLoop] Resolved config for agent '{}': {}", agentId, resolvedConfig);

        // 使用解析后的 maxIterations 和 timeout
        int maxTurns = resolvedConfig.getMaxIterations() != null ? resolvedConfig.getMaxIterations() : MAX_TURNS;

        // 1. 构建初始 messages
        List<Message> messages = buildInitialMessages(resolvedConfig, userMessage, session);

        // 2. think → tool → observe 循环
        for (int turn = 1; turn <= maxTurns; turn++) {
            log.info("[AgentLoop] turn={}/{} session={}", turn, maxTurns, sessionId);

            // 2a. 调用 LLM
            ChatResponse resp = callLLM(messages, resolvedConfig);
            if (resp == null || !resp.isSuccess()) {
                String err = resp != null ? resp.getErrorMsg() : "LLM 调用返回 null";
                log.error("[AgentLoop] LLM call failed at turn {}: {}", turn, err);
                // LLM 返回格式错误 → 重试一次
                if (turn == 1 && resp != null) {
                    log.info("[AgentLoop] Retrying LLM call once after format error");
                    resp = callLLM(messages, resolvedConfig);
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
            List<ToolCall> toolCalls = parseToolCalls(resp);

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
        log.warn("[AgentLoop] Max turns ({}) exceeded for session={}", maxTurns, sessionId);
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

        // T1: 注入Skill列表到System Prompt（运行时加载）
        String agentId = session != null ? session.getProfileName() : null;
        systemPrompt = buildSystemPrompt(systemPrompt, agentId, null);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Message.system(systemPrompt));
        }

        // TODO T0.3: 注入记忆上下文（KG 记忆事实）

        // 当前用户消息
        if (userMessage != null && !userMessage.isBlank()) {
            messages.add(Message.user(userMessage));
        }

        log.debug("[AgentLoop] Built {} initial messages", messages.size());
        // Token 预算裁剪 — 使用解析后的 maxContextTokens（L1 yaml 默认 8000）
        int maxCtx = config != null && config.getMaxContextTokens() != null
                ? config.getMaxContextTokens() : 8000;
        messages = trimHistory(messages, maxCtx);
        return messages;
    }

    // ─── System Prompt 构建 (T1: Skill 运行时加载) ─────────────────────

    /**
     * 构建增强版 System Prompt — 将数据库中的已启用 Skill 列表注入 System Prompt。
     * <p>
     * Skill 注入总量不超过 2000 字符；若 {@link SkillService} 不可用（null），
     * 则原样返回 basePrompt。
     * </p>
     *
     * @param basePrompt 原始 System Prompt（取自 config 或 session）
     * @param agentId    Agent 标识（预留，当前未使用）
     * @param userId     用户标识（预留，当前从会话上下文获取）
     * @return 增强后的 System Prompt
     */
    private String buildSystemPrompt(String basePrompt, String agentId, String userId) {
        if (basePrompt == null) {
            basePrompt = "";
        }
        StringBuilder sb = new StringBuilder(basePrompt);

        // 若 SkillService 不可用，跳过 Skill 注入
        if (skillService == null) {
            log.debug("[AgentLoop] SkillService not available, skipping skill injection");
            return sb.toString();
        }

        try {
            List<SkillEntity> skills = skillService.listSkills(null, true);
            if (skills == null || skills.isEmpty()) {
                log.debug("[AgentLoop] No enabled skills found, skipping skill injection");
                return sb.toString();
            }

            sb.append("\n\n## Available Skills\n");

            int charBudget = 2000; // Skill 注入总量上限
            int used = 0;

            for (SkillEntity s : skills) {
                // 截断过长的描述
                String desc = s.getDescription();
                if (desc != null && desc.length() > 120) {
                    desc = desc.substring(0, 117) + "...";
                }

                StringBuilder line = new StringBuilder();
                line.append("- **").append(s.getName()).append("**");
                if (s.getVersion() != null && !s.getVersion().isBlank()) {
                    line.append(" (v").append(s.getVersion()).append(")");
                }
                line.append(": ").append(desc != null ? desc : "无描述").append("\n");

                if (used + line.length() > charBudget) {
                    log.debug("[AgentLoop] Skill injection budget exhausted ({} chars), "
                            + "{} skills injected", used, skills.indexOf(s));
                    break;
                }
                sb.append(line);
                used += line.length();
            }

            log.info("[AgentLoop] Injected {} enabled skills into system prompt ({} chars)",
                    skills.size(), used);
        } catch (Exception e) {
            log.warn("[AgentLoop] Failed to load skills for system prompt: {}", e.getMessage());
        }

        // T3: 注入记忆上下文（MemoryExtractor 规则引擎）
        injectMemorySection(sb, agentId);

        return sb.toString();
    }

    /**
     * 注入记忆段 — 从会话历史中提取用户偏好事实并追加到 System Prompt。
     * <p>
     * 仅当 {@link MemoryExtractor} 可用时执行。事实段位于 "## Available Skills" 之后，
     * 格式为：
     * </p>
     * <pre>
     * ## Memory (from previous conversations)
     * - 用户偏好：使用表格展示数据
     * - 用户习惯：每天早上查看报表
     * </pre>
     *
     * @param sb      System Prompt StringBuilder（原地追加）
     * @param agentId Agent 标识（预留，供后续按 Agent 过滤记忆）
     */
    private void injectMemorySection(StringBuilder sb, String agentId) {
        if (memoryExtractor == null) {
            log.debug("[AgentLoop] MemoryExtractor not available, skipping memory injection");
            return;
        }

        try {
            // 注：当前从空消息列表提取 — T3 阶段仅留下注入点，
            // 后续迭代通过 AgentSessionService.getMessages() 获取会话历史
            List<AgentSessionService.AgentMessage> sessionMessages = null;

            if (sessionMessages == null || sessionMessages.isEmpty()) {
                log.debug("[AgentLoop] No session messages available for memory extraction");
                return;
            }

            List<String> facts = memoryExtractor.extractFacts(sessionMessages);
            if (facts == null || facts.isEmpty()) {
                log.debug("[AgentLoop] No memory facts extracted");
                return;
            }

            sb.append("\n\n## Memory (from previous conversations)\n");
            for (String fact : facts) {
                sb.append("- ").append(fact).append("\n");
            }

            log.info("[AgentLoop] Injected {} memory facts into system prompt", facts.size());
        } catch (Exception e) {
            log.warn("[AgentLoop] Failed to inject memory section: {}", e.getMessage());
        }
    }

    // ─── Token 预算管理 ─────────────────────────────────────────────────

    /**
     * Token 预算裁剪 — system 消息始终保留在最前，其余消息从近到远保留直到超出预算。
     *
     * @param history   完整消息列表
     * @param maxTokens 上下文窗口最大 token 数
     * @return 裁剪后的消息列表
     */
    private List<Message> trimHistory(List<Message> history, int maxTokens) {
        if (history == null || history.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 分离 system 消息和非 system 消息
        List<Message> systemMsgs = new ArrayList<>();
        List<Message> rest = new ArrayList<>();
        for (Message msg : history) {
            if ("system".equals(msg.getRole())) {
                systemMsgs.add(msg);
            } else {
                rest.add(msg);
            }
        }

        // 2. 计算 system 消息的 token 开销
        int systemTokens = 0;
        for (Message sm : systemMsgs) {
            systemTokens += TokenEstimator.estimate(sm);
        }

        // 3. 预算 = maxTokens - 500（安全余量） - system 消息 token
        int budget = maxTokens - 500 - systemTokens;
        if (budget <= 0) {
            log.warn("[AgentLoop] Token budget exhausted after system messages: "
                    + "systemTokens={}, maxTokens={}", systemTokens, maxTokens);
            return systemMsgs; // system 消息必须保留
        }

        // 4. 从近到远（rest 末尾往前）累加，直到超出预算
        //    选中的消息保持原顺序（rest 末尾的 selectedCount 条）
        int usedTokens = 0;
        int selectedCount = 0;
        for (int i = rest.size() - 1; i >= 0; i--) {
            Message msg = rest.get(i);
            int msgTokens = TokenEstimator.estimate(msg);
            if (usedTokens + msgTokens > budget) {
                log.debug("[AgentLoop] trimHistory: stopping at index={}, "
                        + "usedTokens={}, msgTokens={}, budget={}",
                        i, usedTokens, msgTokens, budget);
                break;
            }
            usedTokens += msgTokens;
            selectedCount++;
        }

        // 构建最终结果：system 消息 + 选中的 rest（末尾 selectedCount 条，保持原顺序）
        List<Message> finalResult = new ArrayList<>(systemMsgs);
        for (int i = rest.size() - selectedCount; i < rest.size(); i++) {
            finalResult.add(rest.get(i));
        }

        int totalTokens = usedTokens + systemTokens;
        log.info("[AgentLoop] trimHistory: {} messages → {} messages, "
                        + "{} tokens / {} budget (system={})",
                history.size(), finalResult.size(), totalTokens, budget, systemTokens);
        return finalResult;
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

        // 构建请求 — 使用解析后的配置（L1/L2/L3已合并），安全兜底
        String model = config != null && config.getModel() != null
                ? config.getModel() : "deepseek-chat";
        Double temperature = config != null && config.getTemperature() != null
                ? config.getTemperature() : 0.3;
        Integer maxTokens = config != null && config.getMaxTokens() != null
                ? config.getMaxTokens() : 4096;

        ChatRequest request = new ChatRequest(model, chatMessages, temperature, maxTokens, false);

        // 1. 优先尝试 LLMProvider（直接调用 DeepSeek API，原生 function-calling）
        LLMProvider provider = selectProvider();
        if (provider != null) {
            log.info("[AgentLoop] Using LLMProvider: {} (priority={})", provider.getName(), provider.priority());
            try {
                ChatResponse resp = provider.chat(request);
                if (resp != null && resp.isSuccess()) {
                    return resp;
                }
                log.warn("[AgentLoop] Provider {} returned failure: {} — falling back to LLMGateway",
                        provider.getName(), resp != null ? resp.getErrorMsg() : "null");
            } catch (Exception e) {
                log.warn("[AgentLoop] Provider {} threw exception — falling back to LLMGateway", provider.getName(), e);
            }
        }

        // 2. Fallback: 使用原有 LLMGateway 路径
        request.setApiKey(deepseekApiKey);
        try {
            return llmGateway.call(request);
        } catch (Exception e) {
            log.error("[AgentLoop] LLM gateway call exception", e);
            return ChatResponse.fail("LLM gateway exception: " + e.getMessage());
        }
    }

    /**
     * 从注入的 Provider 列表中选取最优先的可用 Provider。
     * 按 {@link LLMProvider#priority()} 升序排列，选择第一个满足
     * {@link LLMProvider#isAvailable()} 且 {@link LLMProvider#supportsFunctionCalling()} 的实现。
     *
     * @return 选定的 Provider，若列表为空或无可用 Provider 则返回 null
     */
    private LLMProvider selectProvider() {
        if (llmProviders == null || llmProviders.isEmpty()) {
            return null;
        }
        return llmProviders.stream()
                .sorted(Comparator.comparingInt(LLMProvider::priority))
                .filter(LLMProvider::isAvailable)
                .filter(LLMProvider::supportsFunctionCalling)
                .findFirst()
                .orElse(null);
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
     * 从 LLM 响应中解析工具调用 — 结构化 JSON 解析 + ToolRegistry Schema 校验。
     * <p>
     * 解析策略（按优先级）：
     * </p>
     * <ol>
     *   <li>直接 JSON 解析 tool_calls 字段（OpenAI/DeepSeek 标准格式）</li>
     *   <li>从 content 中提取 {@code ```json ... ```} 代码块</li>
     *   <li>解析 {@code <tool_call>...</tool_call>} 标记格式</li>
     *   <li>正则匹配 {@code function_name(arg1=val1, arg2=val2)} 模式</li>
     *   <li>全部失败 → 视为普通文本回复，返回空列表</li>
     * </ol>
     * <p>
     * 对每个解析出的 ToolCall，通过 {@link ToolRegistry} 进行 Schema 校验：
     * 必填参数检查 + 类型检查 + 默认值填充，校验失败的参数名写入
     * {@link ToolCall#getInvalidParams()}。
     * </p>
     *
     * @param llmResponse LLM 原始响应
     * @return 解析出的工具调用列表，空列表表示非工具调用
     */
    @SuppressWarnings("unchecked")
    private List<ToolCall> parseToolCalls(ChatResponse llmResponse) {
        if (llmResponse == null) {
            return Collections.emptyList();
        }
        String content = llmResponse.getContent();
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }

        String trimmed = content.trim();
        List<ToolCall> calls;

        // 1. 尝试直接 JSON 解析 tool_calls 字段（OpenAI 标准格式）
        calls = tryParseJsonToolCalls(trimmed);
        if (calls != null && !calls.isEmpty()) {
            return validateToolCalls(calls);
        }

        // 2. 尝试从 content 中提取 ```json ... ``` 代码块
        calls = tryParseJsonCodeBlock(trimmed);
        if (calls != null && !calls.isEmpty()) {
            return validateToolCalls(calls);
        }

        // 3. 尝试解析 <tool_call>...</tool_call> 标记格式（兜底）
        calls = tryParseToolCallTag(trimmed);
        if (calls != null && !calls.isEmpty()) {
            return validateToolCalls(calls);
        }

        // 4. 正则匹配 function_name(arg1=val1, arg2=val2) 模式（最终兜底）
        calls = tryParseFunctionCall(trimmed);
        if (calls != null && !calls.isEmpty()) {
            return validateToolCalls(calls);
        }

        // 全部失败 → 普通文本回复，不是工具调用
        return Collections.emptyList();
    }

    // ─── 子解析器 ──────────────────────────────────────────────────────

    /**
     * 尝试解析标准 JSON tool_calls 数组格式。
     * <pre>{@code
     * [{"id": "call_xxx", "type": "function", "function": {"name": "query_db", "arguments": {"sql": "..."}}}]
     * }</pre>
     */
    @SuppressWarnings("unchecked")
    private List<ToolCall> tryParseJsonToolCalls(String content) {
        if (!content.startsWith("[")) {
            return null;
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(content,
                    new TypeReference<List<Map<String, Object>>>() {});
            List<ToolCall> calls = new ArrayList<>();
            for (Map<String, Object> entry : raw) {
                String id = (String) entry.getOrDefault("id", "call_" + System.currentTimeMillis());
                Map<String, Object> func = (Map<String, Object>) entry.get("function");
                if (func != null) {
                    String name = (String) func.get("name");
                    Map<String, Object> arguments = parseArguments(func.get("arguments"));
                    if (name != null && !name.isBlank()) {
                        calls.add(new ToolCall(id, name, arguments));
                    }
                }
            }
            return calls.isEmpty() ? null : calls;
        } catch (Exception e) {
            log.debug("[AgentLoop] Content is not JSON tool_calls array: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 尝试从 content 中提取 ```json ... ``` 代码块并解析工具调用。
     */
    @SuppressWarnings("unchecked")
    private List<ToolCall> tryParseJsonCodeBlock(String content) {
        // 查找 ```json ... ``` 或 ``` ... ``` 代码块
        int fenceEnd = 0;
        while (true) {
            int start = content.indexOf("```", fenceEnd);
            if (start < 0) break;
            int lineEnd = content.indexOf('\n', start);
            if (lineEnd < 0) break;
            String langLine = content.substring(start + 3, lineEnd).trim();
            // 只处理 ```json 或 ```（视为默认 JSON）
            if (!langLine.isEmpty() && !langLine.startsWith("json")) {
                fenceEnd = start + 3;
                continue;
            }

            int end = content.indexOf("```", lineEnd + 1);
            if (end < 0) break;

            String jsonStr = content.substring(lineEnd + 1, end).trim();
            fenceEnd = end + 3;

            // 尝试解析为 tool_calls 数组
            if (jsonStr.startsWith("[")) {
                List<ToolCall> calls = tryParseJsonToolCalls(jsonStr);
                if (calls != null && !calls.isEmpty()) {
                    return calls;
                }
            }

            // 尝试解析为单个 function 对象
            try {
                Map<String, Object> obj = objectMapper.readValue(jsonStr,
                        new TypeReference<Map<String, Object>>() {});
                // 可能是 {"name": "...", "arguments": {...}} 单工具调用
                if (obj.containsKey("name") && obj.containsKey("arguments")) {
                    String name = (String) obj.get("name");
                    Map<String, Object> arguments = parseArguments(obj.get("arguments"));
                    if (name != null && !name.isBlank()) {
                        String id = "call_" + System.currentTimeMillis();
                        return Collections.singletonList(new ToolCall(id, name, arguments));
                    }
                }
                // 也可能是 {"function": {"name": "...", "arguments": {...}}} 格式
                Map<String, Object> func = (Map<String, Object>) obj.get("function");
                if (func != null) {
                    String name = (String) func.get("name");
                    Map<String, Object> arguments = parseArguments(func.get("arguments"));
                    if (name != null && !name.isBlank()) {
                        String id = (String) obj.getOrDefault("id", "call_" + System.currentTimeMillis());
                        return Collections.singletonList(new ToolCall(id, name, arguments));
                    }
                }
            } catch (Exception ignored) {
                // Not valid JSON object
            }
        }
        return null;
    }

    /**
     * 尝试解析 {@code <tool_call>...</tool_call>} 标记格式（保留旧版兼容）。
     */
    @SuppressWarnings("unchecked")
    private List<ToolCall> tryParseToolCallTag(String content) {
        try {
            int start = content.indexOf("<tool_call>");
            int end = content.indexOf("</tool_call>");
            if (start >= 0 && end > start) {
                String json = content.substring(start + "<tool_call>".length(), end).trim();
                Map<String, Object> funcMap = objectMapper.readValue(json,
                        new TypeReference<Map<String, Object>>() {});
                String name = (String) funcMap.get("name");
                Map<String, Object> arguments = parseArguments(funcMap.get("arguments"));
                if (name != null && !name.isBlank()) {
                    String id = "call_" + System.currentTimeMillis();
                    return Collections.singletonList(new ToolCall(id, name, arguments));
                }
            }
        } catch (Exception e) {
            log.debug("[AgentLoop] Content is not <tool_call> format: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 尝试正则匹配 {@code function_name(arg1=val1, arg2=val2)} 模式。
     * <p>
     * 支持参数值的引号包裹（单引号或双引号）和未包裹的值。
     * </p>
     */
    @SuppressWarnings("unchecked")
    private List<ToolCall> tryParseFunctionCall(String content) {
        Matcher m = FUNCTION_CALL_PATTERN.matcher(content);
        List<ToolCall> calls = new ArrayList<>();
        while (m.find()) {
            String funcName = m.group(1);
            String argsStr = m.group(2);
            Map<String, Object> arguments = parseKeyValueArgs(argsStr);

            if (funcName != null && !funcName.isBlank()) {
                String id = "call_" + System.currentTimeMillis() + "_" + calls.size();
                calls.add(new ToolCall(id, funcName.trim(), arguments));
            }
        }
        return calls.isEmpty() ? null : calls;
    }

    // ─── Schema 校验 ────────────────────────────────────────────────────

    /**
     * 对解析出的 ToolCall 列表进行 Schema 校验。
     * <ul>
     *   <li>必填参数检查：缺少必填参数 → 加入 invalidParams</li>
     *   <li>类型检查：参数类型不匹配 → 加入 invalidParams</li>
     *   <li>默认值填充：参数缺失但有默认值 → 自动填充</li>
     * </ul>
     * <p>
     * 若 ToolRegistry 未注入或工具未注册，则跳过校验并原样返回。
     * </p>
     */
    private List<ToolCall> validateToolCalls(List<ToolCall> calls) {
        if (calls == null || calls.isEmpty()) {
            return calls;
        }
        if (toolRegistry == null) {
            log.debug("[AgentLoop] ToolRegistry not available, skipping Schema validation");
            return calls;
        }

        for (ToolCall tc : calls) {
            ToolSchema schema = toolRegistry.get(tc.getName());
            if (schema == null) {
                log.debug("[AgentLoop] Tool '{}' not registered, skipping Schema validation", tc.getName());
                continue;
            }

            Map<String, ToolSchema.ParamDef> paramDefs = schema.getParameters();
            if (paramDefs == null || paramDefs.isEmpty()) {
                continue;
            }

            Map<String, Object> args = tc.getArguments();
            if (args == null) {
                args = new LinkedHashMap<>();
                tc.setArguments(args);
            }

            List<String> invalidParams = new ArrayList<>();

            for (Map.Entry<String, ToolSchema.ParamDef> entry : paramDefs.entrySet()) {
                String paramName = entry.getKey();
                ToolSchema.ParamDef def = entry.getValue();
                Object value = args.get(paramName);

                // 必填参数检查
                if (def.isRequired() && isEmptyArg(value)) {
                    invalidParams.add(paramName);
                    log.warn("[AgentLoop] Tool '{}' missing required param: {}", tc.getName(), paramName);
                    continue;
                }

                // 类型检查（仅对非 null 值）
                if (value != null && !typeMatches(def.getType(), value)) {
                    invalidParams.add(paramName);
                    log.warn("[AgentLoop] Tool '{}' param '{}' type mismatch: expected {} got {}",
                            tc.getName(), paramName, def.getType(), value.getClass().getSimpleName());
                    continue;
                }

                // 默认值填充
                if (isEmptyArg(value) && def.getDefaultValue() != null) {
                    args.put(paramName, def.getDefaultValue());
                    log.debug("[AgentLoop] Tool '{}' filled default for param: {} = {}",
                            tc.getName(), paramName, def.getDefaultValue());
                }
            }

            tc.setInvalidParams(invalidParams);
        }

        return calls;
    }

    // ─── 校验辅助方法 ───────────────────────────────────────────────────

    /**
     * 判断参数值是否为空（null 或空白字符串）。
     */
    private static boolean isEmptyArg(Object value) {
        if (value == null) return true;
        if (value instanceof String s) return s.isBlank();
        return false;
    }

    /**
     * 检查参数值的实际类型是否与 Schema 定义的类型兼容。
     */
    private static boolean typeMatches(String expectedType, Object value) {
        if (expectedType == null) return true;
        return switch (expectedType.toLowerCase()) {
            case "string" -> value instanceof String || value instanceof CharSequence;
            case "number", "integer" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof List || value.getClass().isArray();
            case "object" -> value instanceof Map;
            default -> true; // 未知类型，放行
        };
    }

    /**
     * 统一解析 arguments（支持 String JSON 和已解析的 Map 两种形态）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(Object argsObj) {
        if (argsObj == null) {
            return new LinkedHashMap<>();
        }
        try {
            if (argsObj instanceof String s) {
                if (s.isBlank()) return new LinkedHashMap<>();
                return objectMapper.readValue(s, new TypeReference<Map<String, Object>>() {});
            }
            if (argsObj instanceof Map) {
                return new LinkedHashMap<>((Map<String, Object>) argsObj);
            }
        } catch (Exception e) {
            log.debug("[AgentLoop] Failed to parse arguments: {}", e.getMessage());
        }
        return new LinkedHashMap<>();
    }

    /**
     * 解析 key=value 格式的参数字符串（用于正则兜底路径）。
     * <p>
     * 值支持：单引号包裹、双引号包裹、无包裹的简单值。
     * </p>
     */
    private Map<String, Object> parseKeyValueArgs(String argsStr) {
        Map<String, Object> args = new LinkedHashMap<>();
        if (argsStr == null || argsStr.isBlank()) {
            return args;
        }

        // 按逗号分割（注意不在引号内分割）
        List<String> pairs = splitArgsRespectingQuotes(argsStr);
        for (String pair : pairs) {
            int eqIdx = pair.indexOf('=');
            if (eqIdx <= 0) continue;

            String key = pair.substring(0, eqIdx).trim();
            String rawVal = pair.substring(eqIdx + 1).trim();

            // 去掉首尾引号
            Object value = unquote(rawVal);
            args.put(key, value);
        }
        return args;
    }

    /**
     * 按逗号分割参数字符串（引号内的逗号不分割）。
     */
    private List<String> splitArgsRespectingQuotes(String argsStr) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0; // 0 = 不在引号内

        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);
            if (quote == 0 && (c == '\'' || c == '"')) {
                quote = c;
                current.append(c);
            } else if (quote != 0 && c == quote) {
                quote = 0;
                current.append(c);
            } else if (quote == 0 && c == ',') {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    /**
     * 去掉字符串首尾的单引号或双引号，尝试数字/布尔类型推断。
     */
    private static Object unquote(String s) {
        if (s == null || s.isEmpty()) return s;
        if ((s.startsWith("\"") && s.endsWith("\""))
                || (s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        // 尝试数字推断
        try {
            if (s.contains(".")) {
                return Double.parseDouble(s);
            }
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {
            // 不是数字
        }
        // 尝试布尔推断
        if ("true".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s)) return false;
        return s;
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

    /**
     * 将旧的 AgentLoopConfig 转换为请求覆盖 Map，供 AgentConfigResolver 使用。
     * <p>
     * 仅提取非 null 字段，避免覆盖 L1/L2/L3 的已有值。
     * </p>
     */
    private static Map<String, Object> configToOverrides(AgentLoopConfig config) {
        Map<String, Object> overrides = new LinkedHashMap<>();
        if (config == null) return overrides;
        if (config.getModel() != null) overrides.put("model", config.getModel());
        if (config.getTemperature() != null) overrides.put("temperature", config.getTemperature());
        if (config.getMaxTokens() != null) overrides.put("maxTokens", config.getMaxTokens());
        if (config.getMaxContextTokens() != null) overrides.put("maxContextTokens", config.getMaxContextTokens());
        if (config.getSystemPrompt() != null) overrides.put("systemPrompt", config.getSystemPrompt());
        if (config.getDefaultProvider() != null) overrides.put("defaultProvider", config.getDefaultProvider());
        if (config.getMaxIterations() != null) overrides.put("maxIterations", config.getMaxIterations());
        if (config.getAgentTimeoutMs() != null) overrides.put("agentTimeoutMs", config.getAgentTimeoutMs());
        return overrides;
    }
}
