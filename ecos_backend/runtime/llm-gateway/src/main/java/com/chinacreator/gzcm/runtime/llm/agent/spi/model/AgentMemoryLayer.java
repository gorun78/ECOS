package com.chinacreator.gzcm.runtime.llm.agent.spi.model;

/**
 * SPI 侧记忆分层枚举 —— 与接缝义务 1 的记忆能力配套，独立于 provider 内部枚举。
 *
 * <p>存在意义：让 {@code ai-engine} 在没有 {@code services.agent} compile-time 依赖的
 * 前提下描述分层（铁律 §2.1）。provider 侧实现负责与本枚举互转。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
public enum AgentMemoryLayer {
    /** 当轮工作记忆 */
    WORKING,
    /** 会话内记忆 */
    SESSION,
    /** 跨会话长期记忆 */
    LONG_TERM,
    /** 企业级记忆 */
    ENTERPRISE
}
