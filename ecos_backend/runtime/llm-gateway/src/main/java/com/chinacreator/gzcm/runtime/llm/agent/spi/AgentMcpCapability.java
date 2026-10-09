package com.chinacreator.gzcm.runtime.llm.agent.spi;

import java.util.Map;

/**
 * 接缝义务 2 —— 外部 MCP provider 通道（窄接口）。
 *
 * <p>Hermes 在本设计中降级为「经 SPI 接入的可选外部 provider」：
 * ai-engine 只认识本接口，不认识任何 Hermes 类型；许可 + 供应链审查登记（C238）
 * 是启用该 provider 的前置件，未登记时本能力 {@link #isAvailable()} 即为 false，
 * 调用方必须回显显式不可用错误码而不是空结果。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
public interface AgentMcpCapability {

    /** provider 标识（用于日志/审计回显；不是模型名，不参与计量）。 */
    String providerId();

    /** 通道是否已就绪（未就绪时 {@link #callTool} 不得被调用）。 */
    boolean isAvailable();

    /**
     * 调用一个 MCP 工具。
     *
     * @param toolName 工具名
     * @param args     参数（值类型限 String/Number，实现侧负责序列化）
     * @return provider 返回的 JSON 文本
     * @throws AgentRuntimeCapabilityUnavailableException 通道不可用时
     */
    String callTool(String toolName, Map<String, Object> args);
}
