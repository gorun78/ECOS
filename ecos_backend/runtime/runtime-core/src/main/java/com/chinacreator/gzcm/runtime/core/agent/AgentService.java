package com.chinacreator.gzcm.runtime.core.agent;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Agent 服务接口
 * 
 * 为上层子系统提供统一的 AI Agent 服务。封装 AgentRuntime 的底层细节，
 * 提供简洁的会话管理和提示执行接口。
 * 
 * 使用场景：
 * - Sys-Man：安全审计查询、合规检查 AI 辅助
 * - Bus-Zhi：智能数据采集建议、ETL 编排
 * - Dc-Cheng：元数据发现引导、质量规则推荐
 * - Datanet-Ge：联邦网络协调
 * - AIMod-Ming：NL2SQL、数据标注、模型精调
 *
 * @author CDRC Design Team
 */
public interface AgentService {

    // ── Chat Session Management ──────────────────

    /**
     * 创建新的 Agent 会话
     *
     * @param systemPrompt 系统提示词，定义 Agent 的角色和任务
     * @return 会话 ID
     */
    String createChat(String systemPrompt);

    /**
     * 创建新的 Agent 会话（带标题）
     *
     * @param title        会话标题
     * @param systemPrompt 系统提示词
     * @return 会话 ID
     */
    String createChat(String title, String systemPrompt);

    /**
     * 继续已有会话，发送新消息
     *
     * @param sessionId 会话 ID
     * @param message   用户消息
     * @return Agent 执行结果
     */
    AgentResult continueChat(String sessionId, String message);

    /**
     * 继续已有会话（异步，流式回调）
     *
     * @param sessionId 会话 ID
     * @param message   用户消息
     * @param callback  流式回调（接收每个 LLM token）
     * @return Agent 执行结果
     */
    AgentResult continueChatAsync(String sessionId, String message, Consumer<String> callback);

    // ── One-shot Execution ───────────────────────

    /**
     * 一次性执行提示（无会话上下文）
     *
     * @param prompt 用户提示
     * @return Agent 执行结果
     */
    AgentResult executePrompt(String prompt);

    /**
     * 一次性执行提示（带系统提示词）
     *
     * @param prompt       用户提示
     * @param systemPrompt 系统提示词
     * @return Agent 执行结果
     */
    AgentResult executePrompt(String prompt, String systemPrompt);

    // ── Session Listing ──────────────────────────

    /**
     * 列出所有活跃会话
     *
     * @return 会话信息列表
     */
    List<AgentSessionInfo> listSessions();

    /**
     * 获取指定会话信息
     *
     * @param sessionId 会话 ID
     * @return 会话信息，不存在返回 null
     */
    AgentSessionInfo getSessionInfo(String sessionId);

    // ── Session Management ───────────────────────

    /**
     * 关闭指定会话
     *
     * @param sessionId 会话 ID
     */
    void closeSession(String sessionId);

    /**
     * 关闭所有会话
     */
    void closeAllSessions();

    // ── Status ───────────────────────────────────

    /**
     * 获取 Agent 服务状态
     *
     * @return 状态信息（模型、活跃会话数、token消耗等）
     */
    Map<String, Object> getStatus();

    // ── Shutdown ────────────────────────────────

    /**
     * 关闭 Agent 服务，释放资源
     */
    void shutdown();
}
