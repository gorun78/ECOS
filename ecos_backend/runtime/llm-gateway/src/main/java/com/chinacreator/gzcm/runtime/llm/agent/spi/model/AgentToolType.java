package com.chinacreator.gzcm.runtime.llm.agent.spi.model;

/**
 * SPI 侧工具类型枚举（接缝义务 4 的最小投影）。
 *
 * <p>取值与 provider 侧既有分类对齐，便于 adapter 做无损互转；
 * Tool Contract 17 要素登记落地后，本枚举只增不改。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
public enum AgentToolType {
    OBJECT,
    WORKFLOW,
    API,
    KNOWLEDGE,
    SQL,
    AGENT
}
