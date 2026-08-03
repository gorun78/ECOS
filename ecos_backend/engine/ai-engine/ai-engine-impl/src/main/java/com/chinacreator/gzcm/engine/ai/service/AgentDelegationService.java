package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.runtime.llm.session.AgentSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Agent 委托服务 — 将 {@code delegate_to_agent} 注册为内置工具，实现子 Agent 动态委托。
 *
 * <h3>核心流程</h3>
 * <ol>
 *   <li>LLM 在 AgentLoop 中调用 {@code delegate_to_agent(agentName, instruction)}</li>
 *   <li>{@link ToolExecutorService#executeBuiltin} 路由到此服务的 {@link #delegate(Map)} 方法</li>
 *   <li>创建子 Agent 配置（system prompt 不包含 delegate_to_agent，保证单层委托）</li>
 *   <li>调用 {@link AgentLoopService#run(AgentLoopConfig, String, AgentSession)} 执行子 Agent</li>
 *   <li>结果截断至 3000 字符，返回 {@link ToolExecutorService.ToolResult}</li>
 * </ol>
 *
 * <h3>约束</h3>
 * <ul>
 *   <li>单层委托：子 Agent 不暴露 delegate_to_agent 工具</li>
 *   <li>子 Agent 超时 120s</li>
 *   <li>结果截断 3000 字符</li>
 * </ul>
 */
@Service
public class AgentDelegationService {

    private static final Logger log = LoggerFactory.getLogger(AgentDelegationService.class);

    /** 子 Agent 执行超时（秒） */
    private static final int SUB_AGENT_TIMEOUT_SECONDS = 120;

    /** 子 Agent 结果最大字符数（超过截断） */
    private static final int SUB_AGENT_RESULT_MAX_CHARS = 3000;

    /** 子 Agent 默认模型 */
    private static final String DEFAULT_MODEL = "deepseek-chat";

    /** 子 Agent 默认温度 */
    private static final double DEFAULT_TEMPERATURE = 0.7;

    /** 子 Agent 默认最大输出 token */
    private static final int DEFAULT_MAX_TOKENS = 4096;

    @Autowired
    private AgentLoopService agentLoopService;

    // ─── Public API ────────────────────────────────────────────────────

    /**
     * 执行子 Agent 委托。
     * <p>
     * 参数 {@code params} 必须包含：
     * <ul>
     *   <li>{@code agentName} — 子 Agent 名称（用于 system prompt 标识）</li>
     *   <li>{@code instruction} — 委托给子 Agent 的指令（作为 userMessage）</li>
     * </ul>
     * 可选参数：
     * <ul>
     *   <li>{@code systemPrompt} — 自定义 system prompt（为空时自动生成）</li>
     *   <li>{@code model} — 模型名（默认 deepseek-chat）</li>
     * </ul>
     *
     * @param params 委托参数
     * @return ToolResult，content=子 Agent 输出（截断至 3000 字符），success 标识成功/失败
     */
    public ToolExecutorService.ToolResult delegate(Map<String, Object> params) {
        long startNs = System.nanoTime();
        String callId = "delegate_" + UUID.randomUUID().toString().substring(0, 8);

        // 提取参数
        String agentName = getStringParam(params, "agentName", "sub-agent");
        String instruction = getStringParam(params, "instruction", null);
        String customSystemPrompt = getStringParam(params, "systemPrompt", null);
        String model = getStringParam(params, "model", DEFAULT_MODEL);

        if (instruction == null || instruction.isBlank()) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            return ToolExecutorService.ToolResult.fail(callId, "delegate_to_agent",
                    "缺少必填参数 instruction", elapsedMs);
        }

        log.info("[AgentDelegation] 开始委托子Agent: agent={}, instruction长度={}",
                agentName, instruction.length());

        // 构建子 Agent system prompt — 不包含 delegate_to_agent 工具（单层委托）
        String systemPrompt = customSystemPrompt != null && !customSystemPrompt.isBlank()
                ? customSystemPrompt
                : buildSubAgentSystemPrompt(agentName);

        // 构建子 Agent 配置
        AgentLoopConfig config = new AgentLoopConfig(
                "deepseek",
                model,
                DEFAULT_TEMPERATURE,
                DEFAULT_MAX_TOKENS,
                8000,        // maxContextTokens
                5,           // maxIterations
                SUB_AGENT_TIMEOUT_SECONDS * 1000L,
                systemPrompt
        );

        // 创建子 Agent 空会话
        AgentSession subSession = new AgentSession(
                "sub_" + UUID.randomUUID().toString().substring(0, 8),
                "ai-engine",
                agentName,
                systemPrompt
        );

        // 带超时执行子 Agent
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<AgentLoopResult> future = executor.submit(() ->
                agentLoopService.run(config, instruction, subSession));

        try {
            AgentLoopResult subResult = future.get(SUB_AGENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

            if (subResult.isSuccess()) {
                String content = subResult.getContent();
                String truncated = truncate(content, SUB_AGENT_RESULT_MAX_CHARS);
                log.info("[AgentDelegation] 子Agent完成: agent={}, turns={}, tokens={}, elapsed={}ms",
                        agentName, subResult.getTurns(), subResult.getTotalTokens(), elapsedMs);
                return buildResult(callId, truncated, true, null, elapsedMs, subResult);
            } else {
                String error = subResult.getErrorMsg() != null
                        ? subResult.getErrorMsg() : "子Agent执行失败（success=false）";
                log.warn("[AgentDelegation] 子Agent失败: agent={}, error={}", agentName, error);
                return buildResult(callId, null, false, error, elapsedMs, subResult);
            }
        } catch (TimeoutException e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            log.error("[AgentDelegation] 子Agent超时: agent={}, timeout={}s",
                    agentName, SUB_AGENT_TIMEOUT_SECONDS);
            return ToolExecutorService.ToolResult.fail(callId, "delegate_to_agent",
                    "子Agent超时 (" + SUB_AGENT_TIMEOUT_SECONDS + "s): " + agentName, elapsedMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            return ToolExecutorService.ToolResult.fail(callId, "delegate_to_agent",
                    "子Agent执行被中断: " + agentName, elapsedMs);
        } catch (ExecutionException e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            Throwable cause = e.getCause();
            String msg = cause != null ? cause.getMessage() : e.getMessage();
            log.error("[AgentDelegation] 子Agent异常: agent={}, error={}", agentName, msg, cause);
            return ToolExecutorService.ToolResult.fail(callId, "delegate_to_agent",
                    "子Agent执行异常: " + agentName + " — " + msg, elapsedMs);
        } finally {
            executor.shutdownNow();
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────

    /**
     * 构建子 Agent 的 system prompt。
     * <p>
     * 关键：不包含 delegate_to_agent 工具描述，保证单层委托。
     * </p>
     */
    private String buildSubAgentSystemPrompt(String agentName) {
        return "你是 " + agentName + "，一个被委托执行具体任务的子 Agent。\n"
                + "\n"
                + "你的职责：\n"
                + "1. 根据收到的指令（instruction）独立完成任务\n"
                + "2. 可以使用所有可用工具（数据库查询、知识库搜索等）\n"
                + "3. 完成后返回清晰、完整的结果\n"
                + "\n"
                + "规则：\n"
                + "- 不要询问用户确认，自主完成\n"
                + "- 不要输出《委托给XXX》这类元描述，直接用工具执行\n"
                + "- 5轮推理后自动终止\n"
                + "- 你无法委托给其他Agent（delegate_to_agent 不可用）";
    }

    /**
     * 构建返回的 ToolResult，附加子 Agent 元数据。
     */
    private ToolExecutorService.ToolResult buildResult(String callId, String content,
                                                        boolean success, String error,
                                                        long elapsedMs,
                                                        AgentLoopResult subResult) {
        if (success) {
            // 在 content 前附加元数据段落
            StringBuilder sb = new StringBuilder();
            sb.append("[子Agent: ").append(subResult.getSessionId())
                    .append(" | 轮次: ").append(subResult.getTurns())
                    .append(" | tokens: ").append(subResult.getTotalTokens())
                    .append("]\n\n");
            sb.append(content != null ? content : "");
            return ToolExecutorService.ToolResult.ok(callId, "delegate_to_agent",
                    sb.toString(), elapsedMs);
        } else {
            return ToolExecutorService.ToolResult.fail(callId, "delegate_to_agent",
                    error, elapsedMs);
        }
    }

    private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        if (params == null) return defaultValue;
        Object val = params.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return null;
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "\n\n[子Agent输出已截断，原始长度: " + str.length() + " 字符]";
    }
}
