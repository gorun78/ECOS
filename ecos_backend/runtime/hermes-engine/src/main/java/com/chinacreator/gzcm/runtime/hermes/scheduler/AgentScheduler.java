package com.chinacreator.gzcm.runtime.hermes.scheduler;

import com.chinacreator.gzcm.runtime.hermes.session.AgentSession;

import java.util.concurrent.CompletableFuture;

/**
 * Agent 调度器接口 — 负责任务提交、并发控制、排队
 */
public interface AgentScheduler {

    /**
     * 调度一个 Agent 任务（异步执行）
     *
     * @param session     Agent 会话
     * @param userMessage 用户消息
     * @return CompletableFuture 异步结果
     */
    CompletableFuture<AgentResult> schedule(AgentSession session, String userMessage);

    /**
     * 获取指定子系统当前活跃任务数
     */
    int getActiveCount(String subsystem);

    /**
     * 获取指定子系统排队中的任务数
     */
    int getQueueLength(String subsystem);

    /**
     * 动态调整指定子系统的最大并发数
     */
    void setConcurrency(String subsystem, int maxConcurrency);
}
