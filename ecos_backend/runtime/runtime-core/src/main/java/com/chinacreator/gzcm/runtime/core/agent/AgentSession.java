package com.chinacreator.gzcm.runtime.core.agent;

import java.util.List;

/**
 * Agent 会话接口
 * 
 * 管理单次 Agent 对话的完整生命周期：
 * - 维护对话历史（消息列表）
 * - 管理会话元数据
 * - 控制最大迭代次数
 * - 追踪 Token 消耗
 *
 * @author CDRC Design Team
 */
public interface AgentSession {

    /**
     * 获取会话 ID
     */
    String getId();

    /**
     * 获取会话标题
     */
    String getTitle();

    /**
     * 设置会话标题
     */
    void setTitle(String title);

    /**
     * 获取完整对话历史
     */
    List<AgentMessage> getHistory();

    /**
     * 添加消息到历史
     */
    void addMessage(AgentMessage message);

    /**
     * 获取系统提示词
     */
    String getSystemPrompt();

    /**
     * 设置系统提示词
     */
    void setSystemPrompt(String systemPrompt);

    /**
     * 获取已使用的 Token 总数
     */
    int getTotalTokens();

    /**
     * 累加 Token 使用量
     */
    void addTokens(int tokens);

    /**
     * 获取已执行工具调用次数
     */
    int getToolCallCount();

    /**
     * 增加工具调用计数
     */
    void incrementToolCallCount();

    /**
     * 获取最大迭代次数（默认 10）
     */
    int getMaxIterations();

    /**
     * 设置最大迭代次数
     */
    void setMaxIterations(int maxIterations);

    /**
     * 获取当前迭代次数
     */
    int getCurrentIteration();

    /**
     * 增加迭代计数
     */
    void incrementIteration();

    /**
     * 会话是否已结束
     */
    boolean isCompleted();

    /**
     * 标记会话完成
     */
    void complete();

    /**
     * 获取会话创建时间戳
     */
    long getCreatedAt();

    /**
     * 获取会话元数据
     */
    Object getMetadata(String key);

    /**
     * 设置会话元数据
     */
    void setMetadata(String key, Object value);

    /**
     * 清除会话历史（保留系统提示词）
     */
    void clearHistory();
}
