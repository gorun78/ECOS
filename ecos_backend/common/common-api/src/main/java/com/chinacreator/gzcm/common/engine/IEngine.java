package com.chinacreator.gzcm.common.engine;

import java.util.Map;

/**
 * ECOS 引擎统一接口 — 所有引擎模块的顶层契约。
 * <p>
 * 定义了引擎生命周期（start/stop）、健康检查、配置快照等核心能力。
 * 各具体引擎（安全引擎、数据引擎、本体引擎、认知引擎）必须实现此接口。
 * </p>
 *
 * @author ECOS PMO
 * @since 1.0.0
 */
public interface IEngine {

    /**
     * 引擎唯一标识名称，如 "security-engine"、"data-engine"。
     */
    String getName();

    /**
     * 当前引擎运行状态。
     */
    EngineStatus getStatus();

    /**
     * 当前配置快照（不可变视图），用于审计和诊断。
     */
    Map<String, Object> getConfig();

    /**
     * 执行健康检查，返回各子系统状态。
     */
    HealthCheck healthCheck();

    /**
     * 启动引擎。幂等操作，已启动时调用无副作用。
     */
    void start();

    /**
     * 停止引擎。幂等操作，已停止时调用无副作用。
     */
    void stop();
}
