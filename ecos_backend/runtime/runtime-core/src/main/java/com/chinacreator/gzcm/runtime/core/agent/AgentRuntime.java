package com.chinacreator.gzcm.runtime.core.agent;

import java.util.Map;
import java.util.function.Consumer;

import com.chinacreator.gzcm.runtime.core.agent.llm.LLMClient;
import com.chinacreator.gzcm.runtime.core.agent.llm.LLMConfig;
import com.chinacreator.gzcm.runtime.core.agent.tool.ToolRegistry;

/**
 * Agent 运行时主接口
 * 
 * 作为 Runtime 基座的核心 AI 能力接口，为上层 5 个子系统提供统一的 Agent 服务：
 * - Sys-Man：安全审计 Agent、合规检查 Agent
 * - Bus-Zhi：智能数据采集 Agent、ETL 编排 Agent
 * - Dc-Cheng：智能元数据发现 Agent、质量规则推荐 Agent
 * - Datanet-Ge：联邦网络协调 Agent
 * - AIMod-Ming：NL2SQL Agent、数据标注 Agent、模型精调 Agent
 *
 * 执行流程：
 * 1. 创建会话（createSession）
 * 2. 注册工具（toolRegistry.registerTools）
 * 3. 运行 Agent（run）— 自动循环：LLM 思考 → 工具调用 → 结果反馈 → 最终回答
 * 4. 获取结果（AgentResult）
 *
 * @author CDRC Design Team
 */
public interface AgentRuntime {

    /**
     * 创建新会话
     *
     * @param systemPrompt 系统提示词（定义 Agent 的角色和任务）
     * @return 会话对象
     */
    AgentSession createSession(String systemPrompt);

    /**
     * 创建会话（带标题）
     */
    AgentSession createSession(String title, String systemPrompt);

    /**
     * 获取会话
     */
    AgentSession getSession(String sessionId);

    /**
     * 关闭会话
     */
    void closeSession(String sessionId);

    // ── Agent 运行 ──────────────────────────────

    /**
     * 运行 Agent（同步，阻塞直到完成）
     *
     * @param sessionId 会话 ID
     * @param userMessage 用户输入
     * @return 执行结果
     */
    AgentResult run(String sessionId, String userMessage);

    /**
     * 运行 Agent（异步，通过回调返回结果）
     *
     * @param sessionId 会话 ID
     * @param userMessage 用户输入
     * @param callback StreamCallback 流式回调（每次 LLM token 输出时调用）
     * @return 执行结果
     */
    AgentResult runAsync(String sessionId, String userMessage, Consumer<String> callback);

    /**
     * 运行 Agent（继续已有会话，不添加新用户消息）
     */
    AgentResult continueRun(String sessionId);

    // ── 配置管理 ──────────────────────────────

    /**
     * 获取 LLM 客户端
     */
    LLMClient getLLMClient();

    /**
     * 更新 LLM 配置（热切换模型）
     */
    void updateLLMConfig(LLMConfig config);

    /**
     * 获取工具注册表
     */
    ToolRegistry getToolRegistry();

    /**
     * 获取当前 LLM 配置
     */
    LLMConfig getLLMConfig();

    // ── 状态与统计 ──────────────────────────────

    /**
     * 获取活跃会话数
     */
    int getActiveSessionCount();

    /**
     * 获取全局 Token 消耗统计
     */
    Map<String, Object> getGlobalStats();

    /**
     * 关闭 Agent 运行时（释放资源）
     */
    void shutdown();
}
