package com.chinacreator.gzcm.runtime.llm.agent.spi;

/**
 * 详细设计-06 §八 接缝义务 1/2 —— Agent 运行时能力枚举。
 *
 * <p>每一项对应 ai-engine 需要从 agent-service runtime 取得的一类能力。
 * SPI 以「能力是否可用」为一等概念：承流形态（gateway fat-JAR / aiming service /
 * 单引擎 boot）决定了哪些能力在 classpath 上真实存在，调用方必须能显式区分
 * 「不支持」与「支持但暂不可用」，禁止把两者折叠成静默 no-op。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0（接缝义务 1 依赖方向 / 接缝义务 2 单一 Runtime）
 */
public enum AgentRuntimeCapability {

    /** 会话/长期记忆上下文装配与写入（agent-service {@code runtime.memory}）。 */
    MEMORY,

    /** 目标分解与执行计划生成（agent-service {@code runtime.planner}）。 */
    PLANNER,

    /** 任务执行（agent-service {@code runtime.executor}，非 Agent Loop）。 */
    EXECUTOR,

    /** 工具解析/列表/执行路由（agent-service {@code runtime.toolrouter}）。 */
    TOOL_ROUTER,

    /** 外部 MCP provider 通道（agent-service {@code runtime.mcp}，Hermes 为可选 provider）。 */
    MCP,

    /** 进化任务编排（agent-service {@code runtime.evolution}）。 */
    EVOLUTION;

    /**
     * 供错误响应体回显的稳定标识，取值即枚举名，不含厂商/模型名。
     */
    public String code() {
        return name();
    }
}
