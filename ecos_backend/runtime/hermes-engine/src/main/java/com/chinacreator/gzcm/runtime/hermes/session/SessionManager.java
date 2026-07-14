package com.chinacreator.gzcm.runtime.hermes.session;

import java.util.List;

/**
 * 会话管理器接口 — 管理 Agent 会话生命周期
 */
public interface SessionManager {

    /**
     * 创建新会话
     *
     * @param subsystem    子系统标识
     * @param profileName  profile 名称
     * @param systemPrompt 系统提示词
     * @return 新创建的 sessionId
     */
    String createSession(String subsystem, String profileName, String systemPrompt);

    /**
     * 获取指定会话，超时的会话会被自动标记为 timedout
     *
     * @param sessionId 会话 ID
     * @return AgentSession 或 null（不存在时）
     */
    AgentSession getSession(String sessionId);

    /**
     * 关闭指定会话
     */
    void closeSession(String sessionId);

    /**
     * 获取指定子系统下所有活跃会话
     */
    List<AgentSession> getActiveSessions(String subsystem);

    /**
     * 获取指定子系统下的活跃会话数
     */
    int getActiveSessionCount(String subsystem);
}
