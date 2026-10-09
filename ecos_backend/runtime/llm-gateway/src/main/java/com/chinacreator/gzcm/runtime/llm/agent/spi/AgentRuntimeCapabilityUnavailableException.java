package com.chinacreator.gzcm.runtime.llm.agent.spi;

/**
 * 详细设计-06 §八 接缝义务 1/2 —— {@link AgentRuntimeProvider} 能力不可用异常。
 *
 * <p>语义边界（与仓库既有裁决对齐）：</p>
 * <ul>
 *   <li>本异常表示「SPI 契约在 classpath 上，但该能力在当前承流形态下不可提供」——
 *       调用方（ai-engine Controller）必须回显显式错误码，禁止降级为 200 + 空数据。</li>
 *   <li>它不表示业务失败：业务失败由各能力接口自身的返回值/异常表达。</li>
 * </ul>
 *
 * <p>不携带厂商名或模型名 —— 计量与模型标识唯一来源是 llm-gateway（§2.5 底座收口）。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
public class AgentRuntimeCapabilityUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final AgentRuntimeCapability capability;
    private final String reason;

    public AgentRuntimeCapabilityUnavailableException(AgentRuntimeCapability capability, String reason) {
        super("Agent runtime capability " + (capability == null ? "<unknown>" : capability.name())
                + " unavailable: " + (reason == null ? "no reason reported" : reason));
        this.capability = capability;
        this.reason = reason;
    }

    public AgentRuntimeCapability getCapability() {
        return capability;
    }

    public String getReason() {
        return reason;
    }
}
