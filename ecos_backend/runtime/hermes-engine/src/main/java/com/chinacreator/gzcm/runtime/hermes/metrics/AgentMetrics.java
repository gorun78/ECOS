package com.chinacreator.gzcm.runtime.hermes.metrics;

import com.chinacreator.gzcm.runtime.hermes.model.AgentCallLog;

import java.util.List;
import java.util.Map;

/**
 * 指标收集接口 — 记录调用数据、聚合统计、查询最近日志
 */
public interface AgentMetrics {

    /**
     * 记录一次 LLM 调用
     *
     * @param subsystem    子系统
     * @param profileName  profile 名称
     * @param tokensInput  输入 token 数
     * @param tokensOutput 输出 token 数
     * @param durationMs   耗时 (ms)
     * @param success      是否成功
     */
    void recordCall(String subsystem, String profileName,
                    int tokensInput, int tokensOutput, long durationMs, boolean success);

    /**
     * 获取指定子系统的聚合统计
     *
     * @return Map，包含总调用次数、成功/失败次数、总 token、平均耗时等
     */
    Map<String, Object> getSubsystemStats(String subsystem);

    /**
     * 获取全局聚合统计
     */
    Map<String, Object> getGlobalStats();

    /**
     * 获取指定子系统最近的调用记录
     *
     * @param subsystem 子系统
     * @param limit     条数上限
     * @return 最近调用日志列表
     */
    List<AgentCallLog> getRecentCalls(String subsystem, int limit);
}
