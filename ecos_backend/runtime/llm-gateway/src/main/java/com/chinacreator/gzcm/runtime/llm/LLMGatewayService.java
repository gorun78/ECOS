package com.chinacreator.gzcm.runtime.llm;

import com.chinacreator.gzcm.runtime.llm.model.ProfileConfig;
import com.chinacreator.gzcm.runtime.llm.scheduler.AgentResult;
import com.chinacreator.gzcm.runtime.llm.session.AgentSession;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Hermes 引擎顶层接口 — 统一的 Agent 执行入口
 * <p>
 * 封装会话管理、调度、LLM 调用、指标收集的完整流程。
 * </p>
 *
 * <h3>F06-05 门面归一（详细设计-06 §F06-05 要点 2）</h3>
 * <p>本接口是<b>历史门面</b>：真正的 LLM 出口只有一个 —— {@code runtime.llm.gateway.LLMGateway}
 * 的 {@code call}/{@code stream}（X-3 双门面消除）。本接口的写方法
 * （{@link #execute}、{@link #executeAsync}、{@link #executeWithSession}）只是
 * <b>会话/调度编排壳</b>，其 LLM 外发一律经 {@code AgentScheduler → LLMGateway}，
 * 不形成第二出口；读方法（统计 / profile 目录）保留为兼容读接口。</p>
 * <p>按 ARCH_SPEC §七-2，兼容壳保留 ≥2 迭代后删除；新代码只准注入 {@code LLMGateway}。</p>
 */
@Deprecated(forRemoval = true)
public interface LLMGatewayService {

    /**
     * 同步执行 Agent（内部自动创建会话）
     *
     * @deprecated 写路径兼容壳，仅编排到 {@code LLMGateway}；新代码请直接用
     *             {@code runtime.llm.gateway.LLMGateway#call}（F06-05）
     */
    @Deprecated(forRemoval = true)
    AgentResult execute(String subsystem, String profileName, String userMessage);

    /**
     * 异步执行 Agent — 通过 callback 接收流式 token
     *
     * @param subsystem    子系统
     * @param profileName  profile 名称
     * @param userMessage  用户消息
     * @param callback     流式 token 回调（接收文本片段）
     * @return Agent 执行结果（CompletableFuture 风格，阻塞获取最终结果）
     * @deprecated 写路径兼容壳，见 {@link #execute}
     */
    @Deprecated(forRemoval = true)
    AgentResult executeAsync(String subsystem, String profileName, String userMessage,
                             Consumer<String> callback);

    /**
     * 使用已有会话继续执行
     *
     * @param sessionId   会话 ID
     * @param userMessage 用户消息
     * @return Agent 执行结果
     * @deprecated 写路径兼容壳，见 {@link #execute}
     */
    @Deprecated(forRemoval = true)
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
