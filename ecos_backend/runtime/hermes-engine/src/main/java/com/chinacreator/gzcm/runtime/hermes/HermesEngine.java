package com.chinacreator.gzcm.runtime.hermes;

import com.chinacreator.gzcm.runtime.hermes.model.ProfileConfig;
import com.chinacreator.gzcm.runtime.hermes.scheduler.AgentResult;
import com.chinacreator.gzcm.runtime.hermes.session.AgentSession;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Hermes 引擎顶层接口 — 统一的 Agent 执行入口
 * <p>
 * 封装会话管理、调度、LLM 调用、指标收集的完整流程。
 * </p>
 */
public interface HermesEngine {

    /**
     * 同步执行 Agent（内部自动创建会话）
     *
     * @param subsystem    子系统
     * @param profileName  profile 名称
     * @param userMessage  用户消息
     * @return Agent 执行结果
     */
    AgentResult execute(String subsystem, String profileName, String userMessage);

    /**
     * 异步执行 Agent — 通过 callback 接收流式 token
     *
     * @param subsystem    子系统
     * @param profileName  profile 名称
     * @param userMessage  用户消息
     * @param callback     流式 token 回调（接收文本片段）
     * @return Agent 执行结果（CompletableFuture 风格，阻塞获取最终结果）
     */
    AgentResult executeAsync(String subsystem, String profileName, String userMessage,
                             Consumer<String> callback);

    /**
     * 使用已有会话继续执行
     *
     * @param sessionId   会话 ID
     * @param userMessage 用户消息
     * @return Agent 执行结果
     */
    AgentResult executeWithSession(String sessionId, String userMessage);

    /**
     * 创建新会话（不立即执行）
     *
     * @param subsystem    子系统
     * @param profileName  profile 名称
     * @param userMessage  用户消息（作为首条消息）
     * @return 创建的 AgentSession
     */
    AgentSession createSession(String subsystem, String profileName, String userMessage);

    /**
     * 关闭指定会话
     *
     * @param sessionId 会话 ID
     */
    void closeSession(String sessionId);

    /**
     * 获取指定子系统的统计信息
     *
     * @param subsystem 子系统标识
     * @return 统计 Map
     */
    Map<String, Object> getSubsystemStats(String subsystem);

    /**
     * 获取全局统计信息
     *
     * @return 全局统计 Map
     */
    Map<String, Object> getGlobalStats();

    /**
     * 列出指定子系统下的所有 profile 配置
     *
     * @param subsystem 子系统标识
     * @return Profile 配置列表
     */
    List<ProfileConfig> listProfiles(String subsystem);

    /**
     * 刷新指定子系统的 profile 缓存
     *
     * @param subsystem 子系统标识（null 时刷新全部）
     */
    void refreshProfileCache(String subsystem);
}
